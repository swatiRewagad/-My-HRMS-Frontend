package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per outbound citizen or entity communication, queued for a later sender (UST504-509, 757, 763-764, 506).
 *
 * <p>WHY A TABLE RATHER THAN A DIRECT SEND. Before this, closure dispatch had no durable record at all:
 * {@code CepcWorkflowService.autoDispatchClosureLetter} generated the letter bytes, discarded the return
 * value, stamped {@code closureLetterSentAt} and logged — so the system recorded "sent" for a message that
 * was never addressed to anyone. SMS was a {@code log.info} TODO. A complainant asking "why was I never
 * told" could not be answered from the data.
 *
 * <p>Persisting the intent first and dispatching later separates two failures that must not be conflated:
 * failing to DECIDE to communicate (a workflow bug, fixed in code) and failing to DELIVER (a gateway
 * problem, retried). The workflow's transaction commits the row; a sender drains it afterwards and sets
 * {@link #sent}. If the gateway is down, the row waits — the obligation is not lost, which is the whole
 * point for a statutory closure communication.
 *
 * <p>SENT IS A NULLABLE FLAG WITH A TIMESTAMP, deliberately. {@code sent = false} with a null
 * {@code sentAt} means queued; true with a timestamp means dispatch was ATTEMPTED and accepted by the
 * transport. It does NOT mean the message reached a handset or inbox — no gateway exists yet
 * ({@code LoggingOutboundMessageAdapter}), so nothing here should be read as proof of delivery.
 *
 * <p>BODY AND SMS TEXT ARE STORED RESOLVED, not as a template reference alone. {@link #templateId} records
 * which template produced the text, but the rendered body is kept because a template edited next month
 * must not retroactively change what the system says it told a citizen last month. Both are needed: the
 * pointer for provenance, the text for evidence.
 */
@Entity
@Table(name = "COMMUNICATION_OUTBOX", indexes = {
        @Index(name = "idx_comm_outbox_sent", columnList = "sent"),
        @Index(name = "idx_comm_outbox_complaint", columnList = "relatedReference"),
        @Index(name = "idx_comm_outbox_channel", columnList = "channel"),
        @Index(name = "idx_comm_outbox_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunicationOutbox {

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";

    /** Communication types. Extend rather than reusing a neighbouring value for a new purpose. */
    public static final String TYPE_CLOSURE_LETTER = "CLOSURE_LETTER";
    public static final String TYPE_CLOSURE_SMS = "CLOSURE_SMS";
    public static final String TYPE_ADVISORY_COMPLIED = "ADVISORY_COMPLIED";
    public static final String TYPE_AWARD_PASSED = "AWARD_PASSED";
    public static final String TYPE_IMPLEAD_NOTICE = "IMPLEAD_NOTICE";

    /**
     * The awareness email UST766 sends the complainant when their complaint is referred to an external
     * regulator. A distinct type rather than reusing CLOSURE_LETTER: the idempotency check is per type, so
     * sharing one would make a forward suppress a later closure letter to the same citizen.
     */
    public static final String TYPE_FORWARD_AWARENESS = "FORWARD_AWARENESS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CLOSURE_LETTER | CLOSURE_SMS | ADVISORY_COMPLIED | AWARD_PASSED | IMPLEAD_NOTICE */
    @Column(name = "communication_type", nullable = false, length = 60)
    private String communicationType;

    /** EMAIL or SMS. A column rather than an assumption, so a third channel needs no schema change. */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /**
     * Which COMMUNICATION_TEMPLATES row produced the text. Nullable because a few communications are
     * assembled without a template row, and losing the communication would be worse than losing the
     * provenance pointer.
     */
    @Column(name = "template_id")
    private Long templateId;

    /** Recipient: an email address for EMAIL, a mobile number for SMS. */
    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;

    /** Sender identity. Held per row so a change of sending address does not rewrite history. */
    @Column(name = "sender", length = 320)
    private String sender;

    @Column(name = "subject", length = 500)
    private String subject;

    @Lob
    @Column(name = "body")
    private String body;

    /**
     * The SMS text, kept separate from {@link #body} rather than overloading it. An SMS is not a
     * truncated email — it has its own length budget and its own wording, and storing both on one row
     * lets an operator see exactly what each channel was given for the same event.
     */
    @Column(name = "sms_text", length = 1000)
    private String smsText;

    /** Locale the body/smsText were rendered in, one of the ten supported locales. */
    @Column(name = "language", length = 10)
    private String language;

    /** Complaint or appeal number, for correlation. */
    @Column(name = "related_reference", length = 50)
    private String relatedReference;

    /**
     * False until a sender has dispatched it. Not null so a row can never be ambiguous about whether it
     * still owes a send.
     */
    @Column(name = "sent", nullable = false)
    private boolean sent;

    /** When dispatch was attempted and accepted by the transport. Null while queued. */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** Number of dispatch attempts, so a permanently failing row is visible rather than silently retried. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** Truncated at the service boundary; a stack trace does not belong in an audit row. */
    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
