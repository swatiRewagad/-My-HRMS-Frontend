package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.metrics.MailIntakeMetrics;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The SMTP protocol state machine. One instance per connection (added fresh in
 * {@link SmtpChannelInitializer}). Deliberately tiny protocol surface — brief rule 2: EHLO/HELO,
 * MAIL FROM, RCPT TO (exactly one, must match a configured bot address), DATA, RSET, NOOP, QUIT,
 * optionally STARTTLS. AUTH/VRFY/EXPN are always rejected; anything else is a syntax error.
 *
 * Content-based rejects (loop detection, Auto-Submitted) are deliberately NOT done here — rule 4
 * (never fail-closed on parse errors) argues against bouncing a message at the wire for anything
 * content-derived: we'd rather accept (250), persist durably, and let Stage 4's parser pipeline
 * quarantine it with a clear reason. 550/554 in this class means transport-level policy only
 * (wrong recipient, off-allowlist), never "we didn't like what was inside."
 */
@Slf4j
class SmtpCommandHandler extends SimpleChannelInboundHandler<Object> {

    private final MailIntakeProperties properties;
    private final CidrAllowlist allowlist;
    private final ConnectionTracker connectionTracker;
    private final InboundEmailIngestService ingestService;
    private final MailIntakeMetrics metrics;
    private final SmtpFrameDecoder frameDecoder;
    private final SslContext sslContext; // null if TLS isn't configured

    private final Set<String> botAddresses;

    private SmtpSession session;
    private boolean connectionAccepted = false;
    /** Hard ceiling on total connection duration regardless of activity — distinct from the
     *  ReadTimeoutHandler's per-gap command-timeout-seconds, which a slow-but-steady trickle
     *  never trips. Cancelled on channelInactive. */
    private ScheduledFuture<?> connectionDeadline;

    SmtpCommandHandler(MailIntakeProperties properties, CidrAllowlist allowlist,
                        ConnectionTracker connectionTracker, InboundEmailIngestService ingestService,
                        MailIntakeMetrics metrics, SmtpFrameDecoder frameDecoder, SslContext sslContext) {
        this.properties = properties;
        this.allowlist = allowlist;
        this.connectionTracker = connectionTracker;
        this.ingestService = ingestService;
        this.metrics = metrics;
        this.frameDecoder = frameDecoder;
        this.sslContext = sslContext;
        this.botAddresses = properties.getRecipients().getBotAddresses().stream()
                .map(a -> a.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
        String remoteIp = remote.getAddress().getHostAddress();
        session = new SmtpSession(remoteIp);

        if (!allowlist.isAllowed(remote.getAddress())) {
            metrics.recordRejected("NOT_ALLOWLISTED");
            reply(ctx, SmtpResponses.REJECT_NOT_ALLOWLISTED);
            ctx.close();
            return;
        }
        if (!connectionTracker.tryAcquire(remoteIp)) {
            log.warn("Rejecting connection from {}: concurrent-connection limits reached", remoteIp);
            metrics.recordRejected("CONNECTION_LIMIT");
            reply(ctx, SmtpResponses.TRANSIENT_TOO_BUSY);
            ctx.close();
            return;
        }

        connectionAccepted = true;
        connectionDeadline = ctx.executor().schedule(() -> {
            log.info("Closing connection from {}: exceeded connection-timeout-seconds", remoteIp);
            reply(ctx, SmtpResponses.TRANSIENT_TIMEOUT);
            ctx.close();
        }, properties.getListener().getConnectionTimeoutSeconds(), TimeUnit.SECONDS);

        reply(ctx, SmtpResponses.BANNER);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (connectionDeadline != null) {
            connectionDeadline.cancel(false);
        }
        if (connectionAccepted) {
            connectionTracker.release(session.getRemoteIp());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof ReadTimeoutException) {
            log.info("Closing idle connection from {}", session != null ? session.getRemoteIp() : "unknown");
            reply(ctx, SmtpResponses.TRANSIENT_TIMEOUT);
        } else {
            // Includes TooLongFrameException from SmtpFrameDecoder (oversize line/message).
            log.warn("SMTP protocol error from {}: {}", session != null ? session.getRemoteIp() : "unknown",
                    cause.getMessage());
            reply(ctx, SmtpResponses.REJECT_MESSAGE_TOO_LARGE);
        }
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (!connectionAccepted) {
            return; // connection is already closing; ignore anything still in flight
        }
        if (msg instanceof byte[] data) {
            handleDataComplete(ctx, data);
        } else if (msg instanceof String line) {
            handleCommandLine(ctx, line);
        }
    }

    private void handleCommandLine(ChannelHandlerContext ctx, String line) {
        String upper = line.toUpperCase(Locale.ROOT);

        if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
            session.setHeloDomain(line.length() > 5 ? line.substring(5).trim() : "");
            session.setPhase(SmtpSession.Phase.GREETED);
            boolean tlsAvailable = sslContext != null && !session.isTlsActive();
            reply(ctx, SmtpResponses.ehloExtensions(session.getHeloDomain(),
                    properties.getListener().getMaxMessageSizeBytes(), tlsAvailable));

        } else if (upper.startsWith("STARTTLS")) {
            handleStartTls(ctx);

        } else if (upper.startsWith("MAIL FROM:")) {
            if (properties.getTls().isRequired() && !session.isTlsActive()) {
                metrics.recordRejected("TLS_REQUIRED");
                reply(ctx, SmtpResponses.REJECT_TLS_REQUIRED);
                return;
            }
            session.setMailFrom(extractAddress(line.substring("MAIL FROM:".length())));
            session.setPhase(SmtpSession.Phase.MAIL);
            reply(ctx, SmtpResponses.MAIL_OK);

        } else if (upper.startsWith("RCPT TO:")) {
            handleRcptTo(ctx, line);

        } else if (upper.equals("DATA")) {
            handleDataStart(ctx);

        } else if (upper.startsWith("AUTH")) {
            reply(ctx, SmtpResponses.REJECT_AUTH_NOT_SUPPORTED);

        } else if (upper.startsWith("VRFY") || upper.startsWith("EXPN")) {
            reply(ctx, SmtpResponses.REJECT_VRFY_NOT_SUPPORTED);

        } else if (upper.equals("RSET")) {
            session.resetTransaction();
            reply(ctx, SmtpResponses.RSET_OK);

        } else if (upper.equals("NOOP")) {
            reply(ctx, SmtpResponses.NOOP_OK);

        } else if (upper.equals("QUIT")) {
            ctx.writeAndFlush(SmtpResponses.QUIT_BYE + "\r\n", ctx.newPromise())
                    .addListener(ChannelFutureListener.CLOSE);

        } else {
            reply(ctx, SmtpResponses.REJECT_SYNTAX_ERROR);
        }
    }

    private void handleRcptTo(ChannelHandlerContext ctx, String line) {
        if (session.getMailFrom() == null) {
            reply(ctx, SmtpResponses.REJECT_COMMAND_OUT_OF_SEQUENCE);
            return;
        }
        if (session.getRcptTo() != null) {
            // rule 2 depends on max-recipients-per-transaction staying 1 — see MailIntakeProperties
            metrics.recordRejected("MULTIPLE_RECIPIENTS");
            reply(ctx, SmtpResponses.REJECT_MULTIPLE_RECIPIENTS);
            return;
        }
        String address = extractAddress(line.substring("RCPT TO:".length()));
        if (!botAddresses.contains(address.toLowerCase(Locale.ROOT))) {
            log.warn("Rejecting RCPT TO {} from {}: not a configured bot address (not a relay)",
                    address, session.getRemoteIp());
            metrics.recordRejected("NOT_A_RELAY");
            reply(ctx, SmtpResponses.REJECT_NOT_A_RELAY);
            return;
        }
        session.setRcptTo(address);
        session.setPhase(SmtpSession.Phase.RCPT);
        reply(ctx, SmtpResponses.RCPT_OK);
    }

    private void handleDataStart(ChannelHandlerContext ctx) {
        if (session.getPhase() != SmtpSession.Phase.RCPT) {
            reply(ctx, SmtpResponses.REJECT_COMMAND_OUT_OF_SEQUENCE);
            return;
        }
        session.setPhase(SmtpSession.Phase.DATA);
        frameDecoder.enterDataMode();
        reply(ctx, SmtpResponses.DATA_START);
    }

    private void handleDataComplete(ChannelHandlerContext ctx, byte[] rawBytes) {
        frameDecoder.exitDataMode();
        metrics.recordReceived();

        try {
            InboundEmailIngestService.IngestOutcome outcome = ingestService.ingest(
                    rawBytes, session.getMailFrom(), session.getRcptTo(), session.getRemoteIp());
            try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", "mail-" + outcome.email().getId())) {
                // PII discipline (rule 6): log only id/size/status, never the body or envelope
                // addresses unmasked.
                log.info("Accepted inbound_email_id={} size={} duplicate={} remoteIp={}",
                        outcome.email().getId(), rawBytes.length, outcome.alreadyExisted(), session.getRemoteIp());
            }
            metrics.recordAccepted(outcome.alreadyExisted());
            reply(ctx, "250 OK id=" + outcome.email().getId());
        } catch (IOException | RuntimeException e) {
            // Rule 1: durability wasn't confirmed — 451, sender retries, nothing was silently lost.
            log.error("Failed to durably persist inbound message from {} (size={}): {}",
                    session.getRemoteIp(), rawBytes.length, e.getMessage());
            metrics.recordRejected("STORAGE_FAILURE");
            reply(ctx, SmtpResponses.TRANSIENT_STORAGE_FAILURE);
        } finally {
            session.resetTransaction();
        }
    }

    private void handleStartTls(ChannelHandlerContext ctx) {
        if (sslContext == null) {
            reply(ctx, SmtpResponses.REJECT_TLS_NOT_SUPPORTED);
            return;
        }
        if (session.isTlsActive()) {
            reply(ctx, SmtpResponses.REJECT_TLS_ALREADY_ACTIVE);
            return;
        }
        ctx.writeAndFlush(SmtpResponses.STARTTLS_READY + "\r\n").addListener(future -> {
            if (!future.isSuccess()) {
                ctx.close();
                return;
            }
            // Insert at the front: everything downstream (frame decoder, this handler) keeps
            // working unchanged since SslHandler transparently decrypts before passing bytes on.
            ctx.pipeline().addFirst("ssl", sslContext.newHandler(ctx.alloc()));
            session.setTlsActive(true);
            // RFC 3207: all prior transaction/session state is discarded; client must re-EHLO.
            session.setHeloDomain(null);
            session.resetTransaction();
            session.setPhase(SmtpSession.Phase.CONNECTED);
        });
    }

    /** Pulls the address out of "<addr>" or a bare address, ignoring ESMTP parameters like
     *  "SIZE=12345" that may follow. Deliberately permissive — we're not validating the envelope
     *  sender/recipient's RFC 5321 grammar strictly, only extracting it; RCPT TO's membership
     *  check against botAddresses is the actual gate. */
    private static String extractAddress(String rest) {
        String trimmed = rest.trim();
        int lt = trimmed.indexOf('<');
        int gt = trimmed.indexOf('>');
        if (lt >= 0 && gt > lt) {
            return trimmed.substring(lt + 1, gt).trim();
        }
        int space = trimmed.indexOf(' ');
        return (space > 0 ? trimmed.substring(0, space) : trimmed).trim();
    }

    private void reply(ChannelHandlerContext ctx, String response) {
        ctx.writeAndFlush(response + "\r\n", ctx.newPromise());
    }
}
