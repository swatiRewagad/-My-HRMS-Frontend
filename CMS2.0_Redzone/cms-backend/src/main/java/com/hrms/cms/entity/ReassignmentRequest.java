package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A request to move one RE record from one nodal officer to another, pending PNO approval
 * (UST839, UST840, UST842, UST843).
 *
 * <p>Shape follows {@link InterOfficeTransfer}: String status vocabulary rather than a Java enum,
 * hand-rolled {@code @PrePersist} defaults, plain String actor ids. That is the native convention
 * here — only 2 of 63 entities use {@code @Enumerated} — and matching it keeps the reassignment
 * rows readable alongside the transfer rows staff already know.
 *
 * <p><b>The reason is immutable (UST840).</b> {@code reason} is {@code updatable = false}, so no
 * code path can rewrite it — not a service, not an admin endpoint, not a bulk correction. A
 * reassignment reason is the justification a PNO relied on when approving; if it could be edited
 * afterwards the approval record would attest to a rationale that was never actually reviewed.
 * Corrections are appended as {@link ReassignmentClarification} rows with their own timestamps and
 * authors, exactly as the query threads append follow-up messages rather than editing history.
 */
@Entity
@Table(name = "REASSIGNMENT_REQUESTS", indexes = {
    @Index(name = "idx_rr_status", columnList = "status"),
    @Index(name = "idx_rr_entity_status", columnList = "entityCode,status"),
    @Index(name = "idx_rr_requested_by", columnList = "requestedBy"),
    @Index(name = "idx_rr_record", columnList = "nodalOfficerRecordId"),
    @Index(name = "idx_rr_requested_at", columnList = "requestedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReassignmentRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    /** Withdrawn by the requester. Distinct from REJECTED, which is an independent PNO decision. */
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The record being moved. */
    @Column(nullable = false)
    private Long nodalOfficerRecordId;

    /** Denormalised for the history report so it survives the record being reassigned again. */
    @Column(nullable = false, length = 50)
    private String complaintNumber;

    @Column(nullable = false, length = 50)
    private String entityCode;

    @Column(length = 200)
    private String fromUserId;

    @Column(nullable = false, length = 200)
    private String toUserId;

    /**
     * Names captured at request time, not resolved on read. An officer who later leaves and is
     * deactivated must still appear by name in the audit trail (UST844).
     */
    @Column(length = 200)
    private String fromUserName;

    @Column(length = 200)
    private String toUserName;

    /**
     * The original justification. Never updatable — see the class note. Clarifications go in
     * REASSIGNMENT_CLARIFICATIONS.
     */
    @Column(columnDefinition = "TEXT", updatable = false)
    private String reason;

    /** PENDING | APPROVED | REJECTED | WITHDRAWN */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 200)
    private String requestedBy;

    @Column(length = 200)
    private String requestedByName;

    private LocalDateTime requestedAt;

    @Column(length = 200)
    private String decidedBy;

    private LocalDateTime decidedAt;

    /** Required when rejecting, so a refusal is always explicable to the requester. */
    @Column(columnDefinition = "TEXT")
    private String decisionComment;

    /**
     * Workload of the destination officer at the moment the request was raised, snapshotted.
     *
     * <p>Recomputing this at approval time would show the PNO a different number from the one the
     * requester acted on, and a later reassignment elsewhere would retroactively change what the
     * request appears to have proposed.
     */
    private Integer toUserWorkloadAtRequest;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (status == null) status = STATUS_PENDING;
    }
}
