package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * One append-only row per completed ownership change, feeding the reassignment history report
 * (UST844).
 *
 * <p>Written here rather than to COMPLAINT_TIMELINE deliberately. The timeline is the citizen- and
 * staff-facing narrative of what happened to a complaint; an internal RE staffing change is not an
 * event in that narrative, and mixing the two would put entity-internal personnel movements into a
 * feed shared more widely than the entity.
 *
 * <p>Separate from {@link ReassignmentRequest} because the two answer different questions. The
 * request records an <em>intent</em> and its decision, including rejections and withdrawals that
 * never moved anything. This records the movements that actually took effect, which is what a
 * history report must count — reporting from the request table would inflate the numbers with
 * refused requests.
 */
@Entity
@Table(name = "REASSIGNMENT_HISTORY", indexes = {
    @Index(name = "idx_rh_entity_code", columnList = "entityCode"),
    @Index(name = "idx_rh_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_rh_from_user", columnList = "fromUserId"),
    @Index(name = "idx_rh_to_user", columnList = "toUserId"),
    @Index(name = "idx_rh_reassigned_at", columnList = "reassignedAt"),
    @Index(name = "idx_rh_entity_at", columnList = "entityCode,reassignedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReassignmentHistory {

    /** Moved by a PNO approving a request raised by someone else. */
    public static final String TRIGGER_APPROVED_REQUEST = "APPROVED_REQUEST";
    /** Moved directly by a PNO acting within their own entity, no separate approval step. */
    public static final String TRIGGER_DIRECT = "DIRECT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long nodalOfficerRecordId;

    @Column(nullable = false, length = 50)
    private String complaintNumber;

    @Column(nullable = false, length = 50)
    private String entityCode;

    @Column(length = 200, updatable = false)
    private String fromUserId;

    @Column(length = 200, updatable = false)
    private String fromUserName;

    @Column(nullable = false, length = 200, updatable = false)
    private String toUserId;

    @Column(length = 200, updatable = false)
    private String toUserName;

    /** APPROVED_REQUEST | DIRECT */
    @Column(nullable = false, length = 30, updatable = false)
    private String triggerType;

    /** Null for a direct move, which has no request behind it. */
    private Long reassignmentRequestId;

    /**
     * Copied from the request rather than referenced, so the report reads without a join and stays
     * correct if the request is later superseded.
     */
    @Column(columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(nullable = false, length = 200, updatable = false)
    private String performedBy;

    @Column(length = 200, updatable = false)
    private String performedByName;

    @Column(updatable = false)
    private LocalDateTime reassignedAt;

    @PrePersist
    protected void onCreate() {
        if (reassignedAt == null) reassignedAt = LocalDateTime.now();
    }
}
