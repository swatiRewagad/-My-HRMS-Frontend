package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "SIMULATED_EMAILS", indexes = {
    @Index(name = "idx_email_thread", columnList = "threadId"),
    @Index(name = "idx_email_direction", columnList = "direction"),
    @Index(name = "idx_email_complaint", columnList = "COMPLAINT_ID"),
    @Index(name = "idx_email_complaint_number", columnList = "complaintNumber"),
    @Index(name = "idx_email_sent_at", columnList = "sentAt"),
    @Index(name = "idx_email_contact_person", columnList = "contactPersonId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SimulatedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String messageId;

    @Column(nullable = false, length = 100)
    private String threadId;

    @Column(nullable = false, length = 200)
    private String fromEmail;

    @Column(nullable = false, length = 200)
    private String toEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "COMPLAINT_ID")
    private Long complaintId;

    @Column(length = 50)
    private String complaintNumber;

    /**
     * Scopes this email to one contact person's own thread rather than the complaint's shared log.
     *
     * <p>Null on every pre-existing row and on everything the complaint-level Email Communication tab
     * writes — that log stays shared across the whole complaint. Set only by the per-contact-person
     * Email Communication tab.
     */
    private Long contactPersonId;

    @Column(length = 500)
    private String attachmentUrl;

    /**
     * The communication template this message was rendered from (UST593).
     *
     * <p>UST593 requires the email log to name the template used. Nullable because inbound mail and
     * hand-written replies have none, and because every pre-existing row predates the column.
     */
    @Column(name = "template_used", length = 200)
    private String templateUsed;

    /** CC recipients, comma-separated (UST591 Reply All). Nullable on every pre-existing row. */
    @Column(name = "cc_recipients", length = 1000)
    private String ccRecipients;

    /** BCC recipients, comma-separated. Nullable: every pre-existing row predates the column. */
    @Column(name = "bcc_recipients", length = 1000)
    private String bccRecipients;

    /**
     * Why the last dispatch attempt failed, populated only while {@link #STATUS_FAILED}.
     *
     * <p>Kept on the row rather than only in the log, because the officer is the one who has to decide
     * whether to retry or correct the address, and "it failed" without a reason is not a decidable state.
     */
    @Column(name = "last_error", length = 1000)
    private String lastError;

    /** The officer this mail is with — the draft's author, or whoever sent it. */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** The message this one replies to or forwards, so a thread can be walked without parsing bodies. */
    @Column(name = "in_reply_to_id")
    private Long inReplyToId;

    /** Dispatch attempts made. Null on rows that predate the column; {@link #retryCountOrZero()} reads it. */
    @Column(name = "retry_count")
    private Integer retryCount;

    private LocalDateTime sentAt;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * The outbound lifecycle the Email Communication tab buckets on.
     *
     * <p>DRAFT is composed but not submitted, PENDING is submitted and awaiting dispatch, SENT has gone out
     * and FAILED could not be dispatched. Inbound mail keeps its own {@code UNREAD}/{@code READ} values —
     * this vocabulary describes sending, and forcing received mail into it would make every inbound message
     * claim to have been sent by this office.
     */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public int retryCountOrZero() {
        return retryCount == null ? 0 : retryCount;
    }

    /**
     * The timestamp this message is ordered and displayed by.
     *
     * <p>A DRAFT has never been sent, so {@code sentAt} on one is the moment it was composed rather than a
     * dispatch time; it is still the right ordering key, which is why the fallback chain ends at
     * {@code receivedAt} rather than returning null for drafts and dropping them out of the log.
     */
    public LocalDateTime effectiveTime() {
        return sentAt != null ? sentAt : receivedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.status == null) this.status = "UNREAD";
        if (this.sentAt == null) this.sentAt = LocalDateTime.now();
        if (this.receivedAt == null) this.receivedAt = LocalDateTime.now();
        if (this.updatedAt == null) this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
