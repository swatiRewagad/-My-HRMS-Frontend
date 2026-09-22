package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Header of a query thread between a Regulated Entity and RBI (CEPC/RBIOS) on a complaint.
 *
 * Replaces the former single-slot ReResponseTracker.queryText, which was overwritten on every
 * new query. Messages live in {@link ComplaintQueryMessage} and are append-only.
 */
@Entity
@Table(name = "COMPLAINT_QUERY", indexes = {
    @Index(name = "idx_cq_complaint", columnList = "complaintId"),
    @Index(name = "idx_cq_pending", columnList = "pendingWith,status"),
    @Index(name = "idx_cq_entity", columnList = "entityCode"),
    @Index(name = "idx_cq_type", columnList = "queryType")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintQuery {

    public static final String TYPE_CLARIFICATION = "CLARIFICATION";
    public static final String TYPE_EXTENSION_REQUEST = "EXTENSION_REQUEST";
    public static final String TYPE_DOCUMENT_REQUEST = "DOCUMENT_REQUEST";
    public static final String TYPE_MEETING_REQUEST = "MEETING_REQUEST";

    public static final String SIDE_RE = "RE";
    public static final String SIDE_RBI = "RBI";
    public static final String PENDING_NONE = "NONE";

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_RESOLVED = "RESOLVED";

    public static final String DECISION_PENDING = "PENDING";
    public static final String DECISION_APPROVED = "APPROVED";
    public static final String DECISION_REJECTED = "REJECTED";

    public static final String MEETING_PENDING = "PENDING";
    public static final String MEETING_ACCEPTED = "ACCEPTED";
    public static final String MEETING_DECLINED = "DECLINED";
    public static final String MEETING_COUNTER_PROPOSED = "COUNTER_PROPOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long complaintId;

    @Column(nullable = false, length = 50)
    private String entityCode;

    @Column(nullable = false, length = 30)
    private String queryType;

    @Column(nullable = false, length = 500)
    private String subject;

    /** RE_TO_RBI or RBI_TO_RE. */
    @Column(nullable = false, length = 20)
    private String direction;

    /** Which side owes the next reply: RE, RBI, or NONE. Drives the "awaiting my response" filter. */
    @Column(nullable = false, length = 10)
    private String pendingWith;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 100)
    private String raisedByUserId;

    @Column(nullable = false, length = 200)
    private String raisedByName;

    @Column(length = 50)
    private String raisedByRole;

    @Column(nullable = false, length = 10)
    private String raisedBySide;

    @Column(nullable = false)
    private LocalDateTime raisedAt;

    private LocalDateTime resolvedAt;

    @Column(length = 100)
    private String resolvedBy;

    // ═══ EXTENSION_REQUEST payload ═══
    private LocalDateTime proposedDeadline;

    @Column(columnDefinition = "TEXT")
    private String extensionReason;

    @Column(length = 20)
    private String decision;

    @Column(length = 100)
    private String decidedBy;

    private LocalDateTime decidedAt;

    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    private LocalDateTime grantedDeadline;

    // ═══ MEETING_REQUEST payload ═══
    @Column(columnDefinition = "TEXT")
    private String meetingPurpose;

    @Column(length = 20)
    private String meetingOutcome;

    @Column(columnDefinition = "TEXT")
    private String meetingDeclineReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.raisedAt == null) {
            this.raisedAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
