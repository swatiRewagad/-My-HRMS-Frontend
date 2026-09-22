package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A notice OWED to a party to an appeal -- one row per recipient per event.
 *
 * <p>Why this table exists rather than a call to a gateway: cms-backend has NO email or SMS
 * capability at all (CitizenAuthController's SMS path is a {@code log.info} TODO and
 * cms-notification-service holds a bare placeholder with no client and no config). The appellant is
 * therefore a party we can address but cannot yet reach.
 *
 * <p>The honest model is an outbox of obligations, not a log of pretend sends. A row is created
 * {@code PENDING} and STAYS pending until a real gateway dispatches it; nothing in Phase 1 sets
 * {@code SENT}. That is deliberate -- it means the system can always answer "what were we required
 * to tell this citizen, and have we actually told them?", and no screen can truthfully claim
 * delivery. {@code attemptCount} / {@code lastError} / {@code nextAttemptAt} are here so the
 * eventual gateway is a dispatcher over this table and needs no schema change.
 *
 * <p>Distinct from NOTIFICATION_DELIVERY_LOG, which records ATTEMPTS already made on the working
 * in-app channel (append-only, SENT|FAILED). This records an OBLIGATION whose channel does not exist
 * yet, so its rows are mutable in status only.
 */
@Entity
@Table(name = "AA_CITIZEN_NOTICE", indexes = {
    @Index(name = "idx_acn_appeal", columnList = "appealNumber"),
    @Index(name = "idx_acn_status", columnList = "status"),
    @Index(name = "idx_acn_dispatch", columnList = "status,nextAttemptAt")
}, uniqueConstraints = {
    // Load-bearing, not just a data-quality nicety: this is what makes the reminder sweep idempotent
    // across pods. cms-backend has no distributed lock, so a concurrent second sweep is stopped by the
    // database rejecting the duplicate rather than by application logic.
    @UniqueConstraint(name = "uk_acn_event_recipient",
            columnNames = {"appealNumber", "eventCode", "recipientRole", "dedupeKey"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AaCitizenNotice {

    /** Owed, not yet dispatched. The only terminal state reachable in Phase 1. */
    public static final String STATUS_PENDING = "PENDING";
    /** A gateway accepted it. Nothing in Phase 1 sets this. */
    public static final String STATUS_SENT = "SENT";
    /** A gateway rejected it permanently. */
    public static final String STATUS_FAILED = "FAILED";
    /** Superseded before dispatch -- e.g. a hearing moved before its notice ever went out. */
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";
    /** No contact detail of any kind is on file; the notice is owed but unaddressable. */
    public static final String CHANNEL_NONE = "NONE";

    public static final String PARTY_APPELLANT = "APPELLANT";
    public static final String PARTY_RESPONDENT = "RESPONDENT";
    public static final String PARTY_OMBUDSMAN = "OMBUDSMAN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String appealNumber;

    /** The workflow event that created the obligation, e.g. HEARING_SCHEDULED. */
    @Column(nullable = false, length = 60, updatable = false)
    private String eventCode;

    /** APPELLANT | RESPONDENT | OMBUDSMAN */
    @Column(nullable = false, length = 30, updatable = false)
    private String recipientRole;

    @Column(length = 200, updatable = false)
    private String recipientName;

    /** Masked at the API boundary, never in the row -- a notice we cannot address is a finding. */
    @Column(length = 200, updatable = false)
    private String recipientEmail;

    @Column(length = 30, updatable = false)
    private String recipientPhone;

    /** EMAIL | SMS | NONE */
    @Column(nullable = false, length = 20, updatable = false)
    private String channel;

    /**
     * Translation key of the notice body, NOT rendered English. The citizen's locale is not known at
     * the time the obligation arises, so the text is resolved at dispatch. This is also why no
     * statutory wording is frozen into this row.
     */
    @Column(nullable = false, length = 200, updatable = false)
    private String messageKey;

    /** JSON of interpolation values (hearing date, venue, appeal number) for the key above. */
    @Lob
    @Column(updatable = false)
    private String messageParams;

    /** PENDING | SENT | FAILED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * Guards against a duplicate obligation for the same event -- e.g. a retried POST. Combined with
     * appealNumber+eventCode+recipientRole in a unique constraint.
     */
    @Column(nullable = false, length = 100, updatable = false)
    private String dedupeKey;

    @Column(nullable = false)
    private Integer attemptCount;

    @Column(length = 1000)
    private String lastError;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime dispatchedAt;

    @Column(nullable = false, length = 200, updatable = false)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = STATUS_PENDING;
        if (attemptCount == null) attemptCount = 0;
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
