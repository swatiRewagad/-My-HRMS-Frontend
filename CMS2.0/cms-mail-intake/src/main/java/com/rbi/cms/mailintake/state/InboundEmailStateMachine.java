package com.rbi.cms.mailintake.state;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailEvent;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.entity.QuarantineReason;
import com.rbi.cms.mailintake.metrics.MailIntakeMetrics;
import com.rbi.cms.mailintake.repository.InboundEmailEventRepository;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.rbi.cms.mailintake.entity.InboundEmailStatus.*;

/**
 * The single place that mutates {@link InboundEmail#getStatus()}. Every transition — success,
 * failure, or quarantine — is validated against {@link #ALLOWED} and writes exactly one
 * {@link InboundEmailEvent} row in the same transaction. Nothing else in this module should call
 * {@code email.setStatus(...)} directly.
 *
 * <pre>
 * RECEIVED → PARSED → NORMALISED → DISPATCHED → PROCESSED
 *      ↓         ↓          ↓            ↓
 *      └─────────┴──────────┴────────────┴──→ QUARANTINED
 *                                         └──→ FAILED (retryable)
 * </pre>
 *
 * FAILED is a real, persisted status, not a flag layered on top of another one — see
 * {@link InboundEmail#getFailedStage()} for which forward stage a retry should re-attempt.
 */
@Service
@RequiredArgsConstructor
public class InboundEmailStateMachine {

    private static final Map<InboundEmailStatus, Set<InboundEmailStatus>> ALLOWED = buildAllowedTransitions();

    private final InboundEmailRepository emailRepository;
    private final InboundEmailEventRepository eventRepository;
    private final MailIntakeProperties properties;
    private final MailIntakeMetrics metrics;

    /** Plain forward/quarantine transition — RECEIVED→PARSED, PARSED→NORMALISED, etc. */
    @Transactional
    public InboundEmail transition(InboundEmail email, InboundEmailStatus to, String actor, String detail) {
        InboundEmailStatus from = email.getStatus();
        requireAllowed(from, to);

        email.setStatus(to);
        if (to != QUARANTINED) {
            email.setQuarantineReason(null);
        }
        if (from == FAILED && to != FAILED) {
            // Successful retry — clear failure bookkeeping.
            email.setFailedStage(null);
            email.setLastError(null);
            email.setNextAttemptAt(null);
        }
        emailRepository.save(email);
        writeEvent(email, from, to, actor, detail);
        return email;
    }

    /**
     * A pipeline stage threw while attempting to reach {@code attemptedStage}. Moves the email to
     * FAILED with exponential backoff, or straight to QUARANTINED with
     * {@link QuarantineReason#MAX_ATTEMPTS_EXCEEDED} once cms.mail.intake.retry.max-attempts is
     * exceeded — per rule 4, the message is never dropped, only ever quarantined.
     */
    @Transactional
    public InboundEmail recordFailure(InboundEmail email, InboundEmailStatus attemptedStage,
                                       String actor, String errorMessage) {
        InboundEmailStatus from = email.getStatus();
        requireAllowed(from, FAILED);

        int attempts = email.getAttemptCount() == null ? 0 : email.getAttemptCount();
        attempts++;
        email.setAttemptCount(attempts);
        email.setLastError(truncate(errorMessage, 2000));

        MailIntakeProperties.Retry retryCfg = properties.getRetry();
        if (attempts > retryCfg.getMaxAttempts()) {
            return quarantine(email, QuarantineReason.MAX_ATTEMPTS_EXCEEDED, actor,
                    "Exceeded max-attempts (" + retryCfg.getMaxAttempts() + ") attempting " + attemptedStage);
        }

        email.setStatus(FAILED);
        email.setFailedStage(attemptedStage);
        email.setNextAttemptAt(Instant.now().plusSeconds(computeBackoffSeconds(attempts, retryCfg)));
        emailRepository.save(email);
        writeEvent(email, from, FAILED, actor,
                "Attempt " + attempts + " failed reaching " + attemptedStage + ": " + errorMessage);
        return email;
    }

    /** Operator- or system-initiated quarantine from any non-terminal status. */
    @Transactional
    public InboundEmail quarantine(InboundEmail email, QuarantineReason reason, String actor, String detail) {
        InboundEmailStatus from = email.getStatus();
        requireAllowed(from, QUARANTINED);

        email.setStatus(QUARANTINED);
        email.setQuarantineReason(reason);
        emailRepository.save(email);
        writeEvent(email, from, QUARANTINED, actor, detail);
        metrics.recordQuarantined(reason.name());
        return email;
    }

    /** Audit-only entry — no status change (an admin downloaded the raw .eml, or corrected
     *  linked-complaint metadata via force-link). Still routed through this class so "every
     *  InboundEmailEvent row is written from here" stays true. */
    @Transactional
    public void recordAuditEvent(InboundEmail email, String actor, String detail) {
        writeEvent(email, email.getStatus(), email.getStatus(), actor, detail);
    }

    /** Operator replay from the admin endpoints (Stage 5) — restarts the pipeline from the top. */
    @Transactional
    public InboundEmail replay(InboundEmail email, String actor) {
        InboundEmailStatus from = email.getStatus();
        requireAllowed(from, RECEIVED);

        email.setStatus(RECEIVED);
        email.setQuarantineReason(null);
        email.setFailedStage(null);
        email.setLastError(null);
        email.setAttemptCount(0);
        email.setNextAttemptAt(null);
        emailRepository.save(email);
        writeEvent(email, from, RECEIVED, actor, "Replayed by operator");
        return email;
    }

    public boolean isAllowed(InboundEmailStatus from, InboundEmailStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    private void requireAllowed(InboundEmailStatus from, InboundEmailStatus to) {
        if (!isAllowed(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }

    private long computeBackoffSeconds(int attempt, MailIntakeProperties.Retry cfg) {
        double raw = cfg.getInitialBackoffSeconds() * Math.pow(cfg.getBackoffMultiplier(), attempt - 1);
        return Math.min((long) raw, cfg.getMaxBackoffSeconds());
    }

    private void writeEvent(InboundEmail email, InboundEmailStatus from, InboundEmailStatus to,
                             String actor, String detail) {
        eventRepository.save(InboundEmailEvent.builder()
                .emailId(email.getId())
                .fromStatus(from)
                .toStatus(to)
                .actor(actor)
                .detail(truncate(detail, 2000))
                .build());
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Map<InboundEmailStatus, Set<InboundEmailStatus>> buildAllowedTransitions() {
        Map<InboundEmailStatus, Set<InboundEmailStatus>> m = new EnumMap<>(InboundEmailStatus.class);
        m.put(RECEIVED, EnumSet.of(PARSED, FAILED, QUARANTINED));
        m.put(PARSED, EnumSet.of(NORMALISED, FAILED, QUARANTINED));
        m.put(NORMALISED, EnumSet.of(DISPATCHED, FAILED, QUARANTINED));
        m.put(DISPATCHED, EnumSet.of(PROCESSED, FAILED, QUARANTINED));
        m.put(PROCESSED, EnumSet.noneOf(InboundEmailStatus.class));
        // FAILED can resolve into whichever forward stage was being attempted, fail again
        // (repeat attempt), or give up to QUARANTINED.
        m.put(FAILED, EnumSet.of(RECEIVED, PARSED, NORMALISED, DISPATCHED, PROCESSED, FAILED, QUARANTINED));
        m.put(QUARANTINED, EnumSet.of(RECEIVED));
        return m;
    }
}
