package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.metrics.MailIntakeMetrics;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
class SmtpChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MailIntakeProperties properties;
    private final CidrAllowlist allowlist;
    private final ConnectionTracker connectionTracker;
    private final InboundEmailIngestService ingestService;
    private final MailIntakeMetrics metrics;
    /** ObjectProvider rather than a plain SslContext field — SmtpTlsConfig's @Bean method
     *  returns null when cms.mail.intake.tls.* isn't configured, and Spring does NOT register an
     *  autowirable "null bean" for plain constructor injection in that case (confirmed by an
     *  UnsatisfiedDependencyException during boot-testing this — this ObjectProvider indirection
     *  is what actually lets STARTTLS be optional). */
    private final ObjectProvider<SslContext> sslContextProvider;

    @Override
    protected void initChannel(SocketChannel ch) {
        MailIntakeProperties.Listener cfg = properties.getListener();
        SslContext sslContext = sslContextProvider.getIfAvailable();

        SmtpFrameDecoder frameDecoder = new SmtpFrameDecoder(8192, cfg.getMaxMessageSizeBytes());

        ch.pipeline().addLast(new ReadTimeoutHandler(cfg.getCommandTimeoutSeconds(), TimeUnit.SECONDS));
        // Outbound: every SmtpResponses constant is a plain String — this is what turns it into
        // bytes on the wire. Its absence is a silent-write-failure trap: writeAndFlush(String)
        // with no encoder in the pipeline fails the write future with nothing listening for it,
        // so the connection just hangs with no server-side error at all — found by hand while
        // verifying against a real client, not something a compiler catches.
        ch.pipeline().addLast(new StringEncoder(CharsetUtil.US_ASCII));
        ch.pipeline().addLast(frameDecoder);
        ch.pipeline().addLast(new SmtpCommandHandler(
                properties, allowlist, connectionTracker, ingestService, metrics, frameDecoder, sslContext));
    }
}
