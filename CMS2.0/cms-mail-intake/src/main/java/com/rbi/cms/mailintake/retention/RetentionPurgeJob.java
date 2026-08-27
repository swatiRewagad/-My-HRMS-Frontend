package com.rbi.cms.mailintake.retention;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailAttachment;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.repository.InboundEmailAttachmentRepository;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import com.rbi.cms.mailintake.smtp.RawMessageStore;
import com.rbi.cms.mailintake.state.InboundEmailStateMachine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Deletes the raw-bytes blob (message + every attachment) for terminal-status emails once they're
 * older than cms.mail.intake.retention.raw-bytes-retention-days — the {@code INBOUND_EMAIL} /
 * {@code INBOUND_EMAIL_ATTACHMENT} / {@code INBOUND_EMAIL_EVENT} rows themselves are never deleted
 * (the regulator-grade audit trail outlives the content it once pointed to). Runs on its own
 * single-thread executor, same pattern as {@link com.rbi.cms.mailintake.parser.ParserScheduler},
 * so a slow purge run never contends with the SMTP accept path or the parse-worker pool.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionPurgeJob {

    private static final String ACTOR = "system:retention-purge";
    private static final List<InboundEmailStatus> TERMINAL =
            List.of(InboundEmailStatus.PROCESSED, InboundEmailStatus.QUARANTINED);

    private final InboundEmailRepository emailRepository;
    private final InboundEmailAttachmentRepository attachmentRepository;
    private final RawMessageStore rawMessageStore;
    private final InboundEmailStateMachine stateMachine;
    private final MailIntakeProperties properties;

    private ExecutorService executor;

    @PostConstruct
    void init() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mail-intake-retention-purge");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /** Once a day is plenty for a 180-day-default retention window; not exposed as its own
     *  property — reuses the day granularity the retention window itself is expressed in. */
    @Scheduled(fixedDelayString = "86400000", initialDelayString = "60000")
    public void purgeDueRows() {
        executor.submit(this::runOnce);
    }

    void runOnce() {
        int retentionDays = properties.getRetention().getRawBytesRetentionDays();
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int batchSize = 200;

        List<InboundEmail> due = emailRepository
                .findByStatusInAndReceivedAtBeforeAndRawPurgedAtIsNullOrderByReceivedAtAsc(
                        TERMINAL, cutoff, PageRequest.of(0, batchSize));

        if (due.isEmpty()) {
            return;
        }
        log.info("Retention purge: {} inbound_email row(s) past {} day retention, purging raw bytes",
                due.size(), retentionDays);

        for (InboundEmail email : due) {
            try {
                purgeOne(email);
            } catch (RuntimeException e) {
                log.error("Retention purge failed for inbound_email_id={}: {}", email.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    void purgeOne(InboundEmail email) {
        List<InboundEmailAttachment> attachments = attachmentRepository.findByEmailId(email.getId());
        for (InboundEmailAttachment attachment : attachments) {
            rawMessageStore.deleteBestEffort(attachment.getStoreUri());
        }
        rawMessageStore.deleteBestEffort(email.getRawStoreUri());

        email.setRawPurgedAt(Instant.now());
        emailRepository.save(email);
        stateMachine.recordAuditEvent(email, ACTOR,
                "Raw bytes purged (message + " + attachments.size() + " attachment(s)) per retention policy");
    }
}
