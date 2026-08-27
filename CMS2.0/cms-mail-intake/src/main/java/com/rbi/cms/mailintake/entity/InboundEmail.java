package com.rbi.cms.mailintake.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row per SMTP transaction accepted by the listener. The row is committed in the same
 * transaction as the durable write of the raw message bytes, before the SMTP {@code 250} is
 * returned — see {@code SmtpMessageHandler} (Stage 3). Everything from {@link #status} onward is
 * mutated by the async parser pipeline / state machine, never by the SMTP listener thread itself.
 */
@Entity
@Table(name = "INBOUND_EMAIL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RFC 5322 Message-ID header, if present. Not unique — retries/dual-delivery can repeat it. */
    @Column(name = "SMTP_MESSAGE_ID", length = 500)
    private String smtpMessageId;

    /** SHA-256 of the raw message bytes. The real dedup key — see idx_inbound_email_sha256. */
    @Column(name = "CONTENT_SHA256", nullable = false, length = 64, unique = true)
    private String contentSha256;

    @Column(name = "ENVELOPE_FROM", length = 320)
    private String envelopeFrom;

    @Column(name = "ENVELOPE_TO", nullable = false, length = 320)
    private String envelopeTo;

    @Column(name = "REMOTE_IP", nullable = false, length = 45)
    private String remoteIp;

    @Column(name = "RECEIVED_AT", nullable = false)
    private Instant receivedAt;

    /** Opaque locator for the encrypted raw-bytes blob — see PayloadEncryptionService. Never a
     *  path built from anything in the message itself. */
    @Column(name = "RAW_STORE_URI", nullable = false, length = 500)
    private String rawStoreUri;

    @Column(name = "RAW_SIZE_BYTES", nullable = false)
    private Long rawSizeBytes;

    /** Set once the Stage 5 retention job has deleted the raw-bytes blob at rawStoreUri. The
     *  metadata row and audit trail are kept regardless — see RetentionPurgeJob. rawStoreUri
     *  itself is left in place afterwards as a historical pointer, not nulled out (the column is
     *  NOT NULL and re-purposing it as a "purged" sentinel would be more confusing than a second
     *  timestamp column). */
    @Column(name = "RAW_PURGED_AT")
    private Instant rawPurgedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private InboundEmailStatus status = InboundEmailStatus.RECEIVED;

    /** Set only while status = FAILED: the status the retry worker should re-attempt reaching. */
    @Enumerated(EnumType.STRING)
    @Column(name = "FAILED_STAGE", length = 20)
    private InboundEmailStatus failedStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "QUARANTINE_REASON", length = 30)
    private QuarantineReason quarantineReason;

    @Column(name = "ATTEMPT_COUNT", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "NEXT_ATTEMPT_AT")
    private Instant nextAttemptAt;

    @Column(name = "LAST_ERROR", length = 2000)
    private String lastError;

    // ── Populated once PARSED ──────────────────────────────────────────────
    @Column(name = "ORIGINAL_FROM", length = 320)
    private String originalFrom;

    @Column(name = "ORIGINAL_SUBJECT", length = 1000)
    private String originalSubject;

    @Column(name = "ORIGINAL_SENT_AT")
    private Instant originalSentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "RESOLVED_BY", length = 30)
    private SenderResolverType resolvedBy;

    /** CMS complaint reference matched in the subject/body against
     *  cms.mail.intake.complaint-ref.regex, so a reply threads onto the existing complaint. */
    @Column(name = "COMPLAINT_REF", length = 100)
    private String complaintRef;

    /** Plain reference column, not a JPA association — Complaint lives in cms-backend, a
     *  separate, non-reactor project on its own schema/database. See Stage 1 findings. */
    @Column(name = "LINKED_COMPLAINT_ID", length = 50)
    private String linkedComplaintId;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (receivedAt == null) receivedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
