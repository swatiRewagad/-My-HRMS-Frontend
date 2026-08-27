package com.rbi.cms.mailintake.metrics;

import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * One place for every counter/timer/gauge the brief asks for, rather than scattering
 * {@code MeterRegistry} lookups across the SMTP/parser/state-machine classes. Those classes each
 * get a single new constructor-injected field (Lombok {@code @RequiredArgsConstructor} already
 * generates the wiring for every one of them except {@code SmtpCommandHandler}, which is hand-
 * constructed per connection in {@code SmtpChannelInitializer} — updated there too) and call one
 * of the {@code record*}/{@code start*} methods below at the existing decision points, rather than
 * this class reaching into their internals.
 */
@Component
@RequiredArgsConstructor
public class MailIntakeMetrics {

    private final MeterRegistry registry;
    private final InboundEmailRepository emailRepository;

    private Counter receivedCounter;
    private Counter acceptedNewCounter;
    private Counter acceptedDuplicateCounter;
    private Timer parseLatencyTimer;

    @PostConstruct
    void init() {
        receivedCounter = registry.counter("mailintake.messages.received");
        acceptedNewCounter = Counter.builder("mailintake.messages.accepted")
                .tag("duplicate", "false").register(registry);
        acceptedDuplicateCounter = Counter.builder("mailintake.messages.accepted")
                .tag("duplicate", "true").register(registry);
        parseLatencyTimer = Timer.builder("mailintake.parse.latency").register(registry);

        Gauge.builder("mailintake.queue.depth", this, MailIntakeMetrics::computeQueueDepth)
                .description("RECEIVED rows plus FAILED rows past their retry time")
                .register(registry);
        Gauge.builder("mailintake.queue.oldest_unprocessed_age_seconds", this,
                        MailIntakeMetrics::computeOldestUnprocessedAgeSeconds)
                .description("Age of the oldest non-terminal inbound_email row")
                .register(registry);
    }

    /** A full SMTP DATA payload was read off the wire — before idempotency/allowlist/etc. */
    public void recordReceived() {
        receivedCounter.increment();
    }

    public void recordAccepted(boolean duplicate) {
        (duplicate ? acceptedDuplicateCounter : acceptedNewCounter).increment();
    }

    /** reason: e.g. NOT_ALLOWLISTED, CONNECTION_LIMIT, NOT_A_RELAY, MULTIPLE_RECIPIENTS,
     *  TLS_REQUIRED, STORAGE_FAILURE — see the SmtpResponses constant used at each call site. */
    public void recordRejected(String reason) {
        registry.counter("mailintake.messages.rejected", "reason", reason).increment();
    }

    public void recordQuarantined(String reason) {
        registry.counter("mailintake.messages.quarantined", "reason", reason).increment();
    }

    public void recordAttachmentScanFailure(String verdict) {
        registry.counter("mailintake.attachments.scan_failures", "verdict", verdict).increment();
    }

    public Timer.Sample startParseTimer() {
        return Timer.start(registry);
    }

    public void stopParseTimer(Timer.Sample sample) {
        sample.stop(parseLatencyTimer);
    }

    private double computeQueueDepth() {
        long received = emailRepository.countByStatus(InboundEmailStatus.RECEIVED);
        long dueFailed = emailRepository.countByStatusAndNextAttemptAtBefore(
                InboundEmailStatus.FAILED, Instant.now());
        return received + dueFailed;
    }

    private double computeOldestUnprocessedAgeSeconds() {
        return emailRepository.findFirstByStatusNotInOrderByReceivedAtAsc(terminalStatuses())
                .map(email -> (double) Duration.between(email.getReceivedAt(), Instant.now()).getSeconds())
                .orElse(0.0);
    }

    private static List<InboundEmailStatus> terminalStatuses() {
        return List.of(InboundEmailStatus.PROCESSED, InboundEmailStatus.QUARANTINED);
    }
}
