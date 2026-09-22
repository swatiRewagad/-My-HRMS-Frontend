package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A proposed meeting slot on a MEETING_REQUEST query thread (UST855).
 *
 * A counter-proposal appends new PROPOSED rows and marks the previous ones SUPERSEDED rather
 * than mutating them, so the negotiation remains auditable.
 */
@Entity
@Table(name = "COMPLAINT_QUERY_SLOT", indexes = {
    @Index(name = "idx_cq_slot_query", columnList = "queryId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintQuerySlot {

    public static final String SLOT_PROPOSED = "PROPOSED";
    public static final String SLOT_ACCEPTED = "ACCEPTED";
    public static final String SLOT_DECLINED = "DECLINED";
    public static final String SLOT_SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long queryId;

    @Column(nullable = false)
    private LocalDateTime proposedStart;

    private LocalDateTime proposedEnd;

    @Column(nullable = false, length = 20)
    private String slotStatus;

    /** RE or RBI. */
    @Column(nullable = false, length = 10)
    private String proposedBySide;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.slotStatus == null) {
            this.slotStatus = SLOT_PROPOSED;
        }
    }
}
