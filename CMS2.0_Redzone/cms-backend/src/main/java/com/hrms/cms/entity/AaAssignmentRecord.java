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
 * One placement of an appeal with an officer.
 *
 * This exists as its own table because the assignment engine needs four facts that APPEALS does not
 * carry — when the placement happened, whether the assignee has picked it up, whether it has already
 * been escalated, and whether it counts against the assignee's threshold — and entity/Appeal.java is
 * owned by another session this cycle. Adding columns there would collide with their work.
 *
 * It also turns out to be the better shape regardless: placements are historical. Keeping a row per
 * placement gives reassignment and escalation an audit trail for free, whereas overwriting a column on
 * the appeal loses who held it before.
 *
 * Exactly one row per appeal should have releasedAt = null; that row is the current holder.
 */
@Entity
@Table(name = "aa_assignment_record", indexes = {
        @Index(name = "idx_aaar_appeal", columnList = "appealNumber"),
        @Index(name = "idx_aaar_holder", columnList = "assignedUserId,releasedAt"),
        @Index(name = "idx_aaar_unclaimed", columnList = "releasedAt,claimedAt,assignedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AaAssignmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appeal_number", nullable = false, length = 50)
    private String appealNumber;

    @Column(name = "assigned_user_id", nullable = false, length = 200)
    private String assignedUserId;

    @Column(name = "role_group", nullable = false, length = 100)
    private String roleGroup;

    /** How this placement was selected. Mirrors AaAssignmentResult.Outcome. */
    @Column(name = "outcome", nullable = false, length = 40)
    private String outcome;

    /**
     * True when this placement must not consume the assignee's capacity.
     *
     * Set for a vernacular override: the record is routed to the one officer who can read it, so
     * charging it against their threshold would push them out of the pool for the very language they
     * were chosen for (story 8).
     */
    @Column(name = "threshold_exempt", nullable = false)
    private boolean thresholdExempt;

    /** Who caused the placement: an admin's user id, or "SYSTEM" for engine-driven assignment. */
    @Column(name = "assigned_by", length = 200)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    /** Set when the assignee first opens the record. Null means unclaimed. */
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    /** Set by the escalation sweep, and used to stop it alerting repeatedly for the same record. */
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    /** Set when the record moves to someone else or leaves the officer's queue. Null = still held. */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @PrePersist
    void stamp() {
        if (this.assignedAt == null) {
            this.assignedAt = LocalDateTime.now();
        }
    }

    public boolean isHeld() {
        return releasedAt == null;
    }

    public boolean isUnclaimed() {
        return releasedAt == null && claimedAt == null;
    }
}
