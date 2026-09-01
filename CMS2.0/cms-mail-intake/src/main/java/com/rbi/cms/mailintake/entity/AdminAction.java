package com.rbi.cms.mailintake.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A pending-or-decided maker-checker request against one {@link InboundEmail} — replay a
 * quarantined/failed message, or force-link one to a complaint the automated resolver chain
 * couldn't confidently match. The requester (maker) and decider (checker) must be different
 * people — enforced in {@code AdminMailIntakeService}, not here; this entity only records the
 * outcome. Rows are never deleted, and a rejected request is not retried in place — a new row is
 * created instead, so the audit trail always shows every attempt.
 */
@Entity
@Table(name = "MAIL_INTAKE_ADMIN_ACTION")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMAIL_ID", nullable = false)
    private Long emailId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACTION_TYPE", nullable = false, length = 20)
    private AdminActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private AdminActionStatus status = AdminActionStatus.PENDING;

    /** Only populated for FORCE_LINK — the complaint reference the maker asserts this email
     *  belongs to. */
    @Column(name = "TARGET_COMPLAINT_ID", length = 50)
    private String targetComplaintId;

    @Column(name = "REQUESTED_BY", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "REQUESTED_AT", nullable = false)
    private Instant requestedAt;

    @Column(name = "REQUEST_REASON", nullable = false, length = 2000)
    private String requestReason;

    @Column(name = "DECIDED_BY", length = 100)
    private String decidedBy;

    @Column(name = "DECIDED_AT")
    private Instant decidedAt;

    @Column(name = "DECISION_NOTE", length = 2000)
    private String decisionNote;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) requestedAt = Instant.now();
    }
}
