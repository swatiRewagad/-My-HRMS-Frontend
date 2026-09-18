package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Who held a case file, in which role, and when they stopped holding it.
 *
 * <p><b>Why this table has to exist.</b> A send-back must return the file to the officer who previously
 * held the target role — not merely to that role. Nothing in the codebase recorded that. CEPC's three
 * send-back arms ({@code CepcWorkflowService:327-349}) leave {@code assignedOfficer} UNCHANGED when the
 * caller supplies no {@code targetUser}, so the file's ROLE flips down to CEPC_DO while the SENDER stays
 * its owner — a reviewer ends up owning a file that is supposed to be back with the dealing official.
 * That is the bug this table exists to make impossible, so its shape is deliberately not CEPC's.
 *
 * <p><b>Why not derive it from AUDIT_LOG.</b> Audit metadata already carries {@code assignedOfficer} on
 * every transition, so the history is technically recoverable by replaying it. It is not used for that
 * because (a) the metadata is an untyped TEXT blob, and (b) audit writes are {@code @Async}, so a
 * send-back performed immediately after a forward could read its own history before the row lands. An
 * assignment decision cannot depend on an eventually-consistent log.
 *
 * <p><b>Append-only.</b> Rows are never updated except to stamp {@link #releasedAt}, and never deleted.
 * The holder history of a case file is evidence about a statutory process.
 */
@Entity
@Table(name = "RBIO_CASE_ASSIGNMENT_HISTORY", indexes = {
        @Index(name = "IDX_RBIO_CAH_COMPLAINT", columnList = "COMPLAINT_NUMBER"),
        @Index(name = "IDX_RBIO_CAH_LOOKUP", columnList = "COMPLAINT_NUMBER, ROLE_NAME, RELEASED_AT")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbioCaseAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_NUMBER", nullable = false, length = 50)
    private String complaintNumber;

    /** The role in which the officer held the file — RBIO_REVIEWER, RBIO_DEALING_OFFICIAL, and so on. */
    @Column(name = "ROLE_NAME", nullable = false, length = 50)
    private String roleName;

    /** The officer's username, matching {@code COMPLAINTS.assigned_officer}. */
    @Column(name = "OFFICER_ID", nullable = false, length = 100)
    private String officerId;

    /** The action that handed the file to this officer, for traceability back to the transition table. */
    @Column(name = "ASSIGNED_BY_ACTION", length = 50)
    private String assignedByAction;

    /** Who performed that action. Null for system-driven assignment such as round-robin on intake. */
    @Column(name = "ASSIGNED_BY", length = 100)
    private String assignedBy;

    @Column(name = "ASSIGNED_AT", nullable = false)
    private LocalDateTime assignedAt;

    /**
     * When the officer stopped holding the file. NULL means they hold it NOW.
     *
     * <p>This is the column the previous-holder lookup keys on: the most recent row for the target role
     * with a non-null {@code releasedAt} is the officer to send back to.
     */
    @Column(name = "RELEASED_AT")
    private LocalDateTime releasedAt;

    public boolean current() {
        return releasedAt == null;
    }
}
