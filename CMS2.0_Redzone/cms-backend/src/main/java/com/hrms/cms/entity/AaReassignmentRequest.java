package com.hrms.cms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An AA officer's request to hand a record to someone else.
 *
 * Forked from the RE reassignment shape rather than sharing it. The RE table requires a
 * NODAL_OFFICER_RECORD_ID and an ENTITY_CODE, both NOT NULL and both meaningless for an appeal, and its
 * service scopes every query by entity and guards on RE_PNO / RE_ADMIN. Parameterising all of that
 * would have meant changing a class the RE flows depend on (UST838-845), so the state machine is
 * reproduced here over appeal numbers instead.
 *
 * The status vocabulary is deliberately identical to the RE one so the two read alike.
 */
@Entity
@Table(name = "aa_reassignment_request", indexes = {
        @Index(name = "idx_aarr_status", columnList = "status"),
        @Index(name = "idx_aarr_appeal", columnList = "appealNumber"),
        @Index(name = "idx_aarr_requested_by", columnList = "requestedBy")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AaReassignmentRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    /** Withdrawn by the requester. Distinct from REJECTED, which is an admin's decision. */
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appeal_number", nullable = false, length = 50)
    private String appealNumber;

    @Column(name = "role_group", length = 100)
    private String roleGroup;

    /** Who currently holds the record. Denormalised so history survives a later reassignment. */
    @Column(name = "from_user_id", nullable = false, length = 200)
    private String fromUserId;

    /**
     * Requested destination, or null to let the engine choose.
     *
     * Null is the common case: an officer asking to be relieved usually has no view on who should take
     * over, and letting them name a colleague would route around the threshold rules.
     */
    @Column(name = "to_user_id", length = 200)
    private String toUserId;

    /** Immutable once written: an audit trail that can be edited afterwards is not one. */
    @Column(name = "reason", nullable = false, length = 1000, updatable = false)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "requested_by", nullable = false, length = 200)
    private String requestedBy;

    @Column(name = "requested_by_role", length = 50)
    private String requestedByRole;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_by", length = 200)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    /** Where the record actually went, which may differ from toUserId when the engine chose. */
    @Column(name = "resolved_to_user_id", length = 200)
    private String resolvedToUserId;

    /** True when a config rule approved this on raise rather than an admin deciding it. */
    @Column(name = "auto_approved", nullable = false)
    private boolean autoApproved;

    @PrePersist
    void stamp() {
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = STATUS_PENDING;
        }
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }
}
