package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.entity.QuarantineReason;
import com.rbi.cms.mailintake.metrics.MailIntakeMetrics;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import com.rbi.cms.mailintake.smtp.RawMessageStore;
import com.rbi.cms.mailintake.spi.HandlerResult;
import com.rbi.cms.mailintake.spi.InboundMailHandler;
import com.rbi.cms.mailintake.spi.NormalisedInboundMail;
import com.rbi.cms.mailintake.state.InboundEmailStateMachine;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.dom.Message;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

/**
 * One RECEIVED (or due-for-retry FAILED) row, taken all the way to PROCESSED or QUARANTINED.
 * Deliberately NOT wrapped in one top-level {@code @Transactional}: each state transition below
 * (via {@link InboundEmailStateMachine}) commits its own small transaction as it happens, so if
 * something fails midway — say, the handler throws right after DISPATCHED was recorded — the
 * audit trail up to that point is durably real, not rolled back along with the failure. Entity
 * field mutations (setOriginalFrom etc.) are applied to the same in-memory {@code email} instance
 * before each transition call, so they ride along with that transition's own save().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParserPipeline {

    private static final String ACTOR = "system:parser-pipeline";

    private final InboundEmailRepository emailRepository;
    private final RawMessageStore rawMessageStore;
    private final MimeMessageFactory mimeMessageFactory;
    private final SenderResolutionChain resolutionChain;
    private final LoopGuardChecker loopGuardChecker;
    private final NormalisedMailBuilder normalisedMailBuilder;
    private final InboundEmailStateMachine stateMachine;
    private final InboundMailHandler mailHandler;
    private final MailIntakeMetrics metrics;

    public void process(Long emailId) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", "mail-" + emailId)) {
            Timer.Sample sample = metrics.startParseTimer();
            try {
                processInternal(emailId);
            } finally {
                metrics.stopParseTimer(sample);
            }
        }
    }

    private void processInternal(Long emailId) {
        InboundEmail email = emailRepository.findById(emailId).orElse(null);
        if (email == null) {
            log.warn("ParserPipeline asked to process inbound_email_id={} but it no longer exists", emailId);
            return;
        }

        byte[] rawBytes;
        try {
            rawBytes = rawMessageStore.read(email.getRawStoreUri());
        } catch (IOException e) {
            log.error("inbound_email_id={}: could not read raw bytes", emailId, e);
            stateMachine.recordFailure(email, InboundEmailStatus.PARSED, ACTOR,
                    "Could not read raw bytes: " + e.getMessage());
            return;
        }

        Message message;
        try {
            message = mimeMessageFactory.parse(rawBytes);
        } catch (Exception e) {
            // A genuinely malformed byte stream won't parse differently on retry — quarantine
            // straight away rather than burning through attempt-count/backoff for nothing.
            log.warn("inbound_email_id={}: unparseable, quarantining", emailId, e);
            stateMachine.quarantine(email, QuarantineReason.UNPARSEABLE_MESSAGE, ACTOR, String.valueOf(e.getMessage()));
            return;
        }
        stateMachine.transition(email, InboundEmailStatus.PARSED, ACTOR, "mime4j parse succeeded");

        Optional<ResolvedSender> resolved;
        try {
            resolved = resolutionChain.resolve(message);
        } catch (Exception e) {
            log.error("inbound_email_id={}: sender-resolution chain threw", emailId, e);
            stateMachine.recordFailure(email, InboundEmailStatus.NORMALISED, ACTOR,
                    "Sender-resolution chain threw: " + e.getMessage());
            return;
        }
        if (resolved.isEmpty()) {
            stateMachine.quarantine(email, QuarantineReason.UNRESOLVED_ORIGINAL_SENDER, ACTOR,
                    "No resolver in the chain produced a confident match");
            return;
        }

        Optional<String> loopReason = loopGuardChecker.check(resolved.get().canonicalMessage());
        if (loopReason.isPresent()) {
            stateMachine.quarantine(email, QuarantineReason.LOOP_DETECTED, ACTOR, loopReason.get());
            return;
        }

        email.setOriginalFrom(resolved.get().originalFrom());
        email.setOriginalSubject(resolved.get().originalSubject());
        email.setOriginalSentAt(resolved.get().originalSentAt());
        email.setResolvedBy(resolved.get().resolverType());

        NormalisedInboundMail normalised;
        try {
            normalised = normalisedMailBuilder.build(email.getId(), resolved.get());
        } catch (Exception e) {
            log.error("inbound_email_id={}: normalisation failed", emailId, e);
            stateMachine.recordFailure(email, InboundEmailStatus.NORMALISED, ACTOR,
                    "Normalisation failed: " + e.getMessage());
            return;
        }
        email.setComplaintRef(normalised.complaintRef());
        stateMachine.transition(email, InboundEmailStatus.NORMALISED, ACTOR,
                "Resolved by " + resolved.get().resolverType());

        stateMachine.transition(email, InboundEmailStatus.DISPATCHED, ACTOR,
                "Dispatching to InboundMailHandler");
        dispatch(email, normalised);
    }

    private void dispatch(InboundEmail email, NormalisedInboundMail normalised) {
        HandlerResult result;
        try {
            result = mailHandler.handle(normalised);
        } catch (Exception e) {
            log.error("inbound_email_id={}: InboundMailHandler threw", email.getId(), e);
            stateMachine.recordFailure(email, InboundEmailStatus.DISPATCHED, ACTOR,
                    "InboundMailHandler threw: " + e.getMessage());
            return;
        }

        switch (result) {
            case HandlerResult.Success success -> {
                email.setLinkedComplaintId(success.linkedComplaintId());
                stateMachine.transition(email, InboundEmailStatus.PROCESSED, ACTOR, "Handler reported success");
            }
            case HandlerResult.Failure failure when failure.retryable() ->
                    stateMachine.recordFailure(email, InboundEmailStatus.DISPATCHED, ACTOR, failure.reason());
            case HandlerResult.Failure failure ->
                    stateMachine.quarantine(email, QuarantineReason.OTHER, ACTOR, failure.reason());
        }
    }
}
