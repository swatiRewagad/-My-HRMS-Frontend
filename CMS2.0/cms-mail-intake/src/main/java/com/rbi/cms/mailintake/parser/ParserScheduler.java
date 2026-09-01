package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The "async workers" from the brief's architecture diagram. The SMTP listener thread (Stage 3)
 * never does any of this — it only ever creates a RECEIVED row and returns 250/451. This poller
 * picks up RECEIVED rows plus FAILED rows whose backoff has elapsed, and fans them out to a fixed
 * worker pool so one slow/stuck parse can't starve the rest of the backlog (also true in spirit
 * of "a slow or broken parser must never block SMTP acceptance" — this pool is entirely separate
 * from the listener's event loop).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ParserScheduler {

    private final InboundEmailRepository emailRepository;
    private final ParserPipeline pipeline;
    private final MailIntakeProperties properties;

    private ExecutorService workerPool;

    @PostConstruct
    void start() {
        workerPool = Executors.newFixedThreadPool(properties.getParser().getWorkerPoolSize());
    }

    @PreDestroy
    void stop() {
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                workerPool.shutdownNow();
            }
        }
    }

    // Property placeholder, not a SpEL bean reference — @ConfigurationPropertiesScan-registered
    // beans don't reliably get the "decapitalized class name" bean name @Scheduled's SpEL would
    // need (confirmed the hard way: boot failed looking for a bean literally named
    // "mailIntakeProperties"). ${...:5} against raw Environment properties is the standard,
    // reliable way to make a fixed delay configurable.
    @Scheduled(fixedDelayString = "${cms.mail.intake.parser.poll-interval-seconds:5}000")
    void pollAndDispatch() {
        int batchSize = properties.getParser().getBatchSize();

        List<InboundEmail> received = emailRepository.findByStatus(InboundEmailStatus.RECEIVED);
        List<InboundEmail> dueRetries = emailRepository.findByStatusAndNextAttemptAtBefore(
                InboundEmailStatus.FAILED, Instant.now());

        Stream.concat(received.stream(), dueRetries.stream())
                .limit(batchSize)
                .forEach(email -> workerPool.submit(() -> {
                    try {
                        pipeline.process(email.getId());
                    } catch (Exception e) {
                        // The pipeline itself already turns known failure modes into FAILED/
                        // QUARANTINED transitions; this is a last-resort net for anything that
                        // still escaped, so one bad message can't kill a worker thread silently.
                        log.error("Unhandled exception processing inbound_email_id={}: {}",
                                email.getId(), e.getMessage(), e);
                    }
                }));
    }
}
