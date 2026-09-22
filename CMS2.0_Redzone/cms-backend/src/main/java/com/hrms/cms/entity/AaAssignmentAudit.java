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
 * Audit trail for every mutation of shared AA assignment state.
 *
 * A separate table rather than CONFIG_AUDIT_LOG because that table has no reason column
 * (config_key / old_value / new_value / changed_by / changed_at only), and every admin action here —
 * threshold edit, activation change, manual assignment, forced placement above threshold — must record
 * WHY. The old->new field shape is copied from it deliberately so the two read alike.
 *
 * Rows are written inside the same transaction as the change they describe. An audit row that can be
 * lost while the change commits is not an audit trail; the officer-deactivation precedent wraps its
 * timeline write in a swallowing catch, which is the failure mode being avoided here.
 */
@Entity
@Table(name = "aa_assignment_audit", indexes = {
        @Index(name = "idx_aaa_audit_action", columnList = "action"),
        @Index(name = "idx_aaa_audit_subject", columnList = "subjectUserId"),
        @Index(name = "idx_aaa_audit_at", columnList = "performedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AaAssignmentAudit {

    /** What kind of change this row records. */
    public static final String ACTION_THRESHOLD_CHANGED = "THRESHOLD_CHANGED";
    public static final String ACTION_ACTIVATION_CHANGED = "ACTIVATION_CHANGED";
    public static final String ACTION_MANUAL_ASSIGNMENT = "MANUAL_ASSIGNMENT";
    public static final String ACTION_VERNACULAR_OVERRIDE = "VERNACULAR_OVERRIDE";
    public static final String ACTION_THRESHOLD_BREACH_GRACE = "THRESHOLD_BREACH_GRACE";
    public static final String ACTION_POINTER_RESET = "POINTER_RESET";
    public static final String ACTION_REBALANCE = "REBALANCE";
    public static final String ACTION_SKILL_CHANGED = "SKILL_CHANGED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    /** The officer the change is about. Null for pool-wide actions such as a pointer reset. */
    @Column(name = "subject_user_id", length = 200)
    private String subjectUserId;

    @Column(name = "role_group", length = 100)
    private String roleGroup;

    /** Appeal number, when the row records a placement rather than a configuration change. */
    @Column(name = "appeal_number", length = 50)
    private String appealNumber;

    @Column(name = "field_name", length = 60)
    private String fieldName;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    /** Free text supplied by the acting admin. Mandatory for manual assignment. */
    @Column(name = "reason", length = 1000)
    private String reason;

    /** Resolved server-side from the JWT, never from the request body. */
    @Column(name = "performed_by", nullable = false, length = 200)
    private String performedBy;

    @Column(name = "performed_by_role", length = 50)
    private String performedByRole;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @PrePersist
    void stamp() {
        if (this.performedAt == null) {
            this.performedAt = LocalDateTime.now();
        }
    }
}
