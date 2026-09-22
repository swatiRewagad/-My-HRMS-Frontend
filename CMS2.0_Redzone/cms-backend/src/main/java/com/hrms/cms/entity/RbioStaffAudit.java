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
 * Audit trail for staff-administration and master-configuration changes (UST452-455, UST459, UST630).
 *
 * <h2>Why a new table rather than an existing one</h2>
 * None of the three existing audit tables can carry a user-scoped change:
 * <ul>
 *   <li>{@code AUDIT_LOG.complaint_number} is NOT NULL, so it cannot represent "this account's
 *       permission changed" at all;</li>
 *   <li>{@code AA_ASSIGNMENT_AUDIT} is AA-namespaced and carries an {@code APPEAL_NUMBER};</li>
 *   <li>{@code CONFIG_AUDIT_LOG} has six columns, no reason and no description, and its only writer
 *       refuses any key not prefixed {@code timeline.}.</li>
 * </ul>
 *
 * <h2>Written in the same transaction as the change</h2>
 * There is no try/catch around the write. An audit row that can be lost while the change it describes
 * commits is not an audit trail — if the audit fails, the mutation must fail with it. UST459 requires
 * these to be retrievable, so a silently swallowed write would make the feature untestable and the
 * record incomplete precisely when something went wrong.
 *
 * <p>{@code performedBy} is resolved from the JWT by the service. Accepting an actor from the request
 * body is what made attribution spoofable in the AA module before it was fixed.
 */
@Entity
@Table(name = "RBIO_STAFF_AUDIT", indexes = {
        @Index(name = "idx_rsa_subject", columnList = "SUBJECT_USER_ID"),
        @Index(name = "idx_rsa_action", columnList = "ACTION"),
        @Index(name = "idx_rsa_at", columnList = "PERFORMED_AT"),
        @Index(name = "idx_rsa_complaint", columnList = "COMPLAINT_NUMBER")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbioStaffAudit {

    public static final String ACTION_USER_CREATED = "USER_CREATED";
    public static final String ACTION_USER_UPDATED = "USER_UPDATED";
    public static final String ACTION_ACTIVATION_CHANGED = "ACTIVATION_CHANGED";
    public static final String ACTION_LEAVE_CHANGED = "LEAVE_CHANGED";
    public static final String ACTION_CLOSURE_ELIGIBILITY_CHANGED = "CLOSURE_ELIGIBILITY_CHANGED";
    public static final String ACTION_OWNER_REASSIGNED = "OWNER_REASSIGNED";
    public static final String ACTION_ROLE_CHANGED = "ROLE_CHANGED";
    public static final String ACTION_MASTER_CHANGED = "MASTER_CHANGED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ACTION", nullable = false, length = 50)
    private String action;

    /** The staff member the action was done to. Null for pool-wide or master-data actions. */
    @Column(name = "SUBJECT_USER_ID", length = 200)
    private String subjectUserId;

    /** For {@link #ACTION_OWNER_REASSIGNED} (UST455): which complaint moved. */
    @Column(name = "COMPLAINT_NUMBER", length = 50)
    private String complaintNumber;

    @Column(name = "OLD_VALUE", length = 500)
    private String oldValue;

    @Column(name = "NEW_VALUE", length = 500)
    private String newValue;

    @Column(name = "FIELD_NAME", length = 60)
    private String fieldName;

    /**
     * Human-readable account of the change. UST459 asks for a description alongside user and timestamp,
     * and {@code CONFIG_AUDIT_LOG} has no such column — which is the main reason this table exists.
     */
    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "REASON", length = 1000)
    private String reason;

    @Column(name = "PERFORMED_BY", nullable = false, length = 200)
    private String performedBy;

    @Column(name = "PERFORMED_BY_ROLE", length = 50)
    private String performedByRole;

    @Column(name = "PERFORMED_AT", nullable = false)
    private LocalDateTime performedAt;

    @PrePersist
    void stamp() {
        if (performedAt == null) {
            performedAt = LocalDateTime.now();
        }
    }
}
