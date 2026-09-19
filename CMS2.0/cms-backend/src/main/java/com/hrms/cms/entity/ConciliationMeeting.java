package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One conciliation meeting on a complaint, as keyed in from the RBIO Conciliation tab.
 * <p>
 * Deliberately a history table rather than the one-row-per-complaint shape its sibling tabs use
 * (COMPLAINT_RBIO_FORM_DATA, COMPLAINT_ADDITIONAL_DETAILS): a meeting can be rescheduled any number
 * of times and RBIO-US-018 requires the earlier attempts to survive, so a reschedule opens a new row
 * instead of overwriting the previous one. The newest row is the live meeting.
 * <p>
 * {@code meetingTime} is a VARCHAR rather than a time column because Oracle has no TIME type and the
 * form only ever collects HH:mm; {@code RbioConciliationService} validates the format on write.
 */
@Entity
@Table(name = "CONCILIATION_MEETINGS", indexes = {
    @Index(name = "idx_cm_complaint", columnList = "complaint_id"),
    @Index(name = "idx_cm_status", columnList = "meeting_status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConciliationMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    /** SCHEDULED, RESCHEDULED, COMPLETED or CANCELLED. The first two are live; the last two close the row. */
    @Column(name = "meeting_status", length = 30)
    private String meetingStatus;

    @Column(name = "meeting_date")                            private LocalDate meetingDate;
    @Column(name = "meeting_time", length = 5)                private String meetingTime;

    @Column(name = "accepted_by_complainant", length = 10)    private String acceptedByComplainant;
    @Column(name = "accepted_by_entity", length = 10)         private String acceptedByEntity;
    @Column(name = "conducted_through_vc", length = 10)       private String conductedThroughVc;

    /** Minutes of the meeting. */
    @Column(name = "meeting_comments", length = 4000)         private String meetingComments;

    /** The officer's own remarks, kept apart from the minutes. */
    @Column(name = "comments", length = 4000)                 private String comments;

    @Column(name = "created_by", length = 200)                private String createdBy;
    @Column(name = "updated_by", length = 200)                private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
