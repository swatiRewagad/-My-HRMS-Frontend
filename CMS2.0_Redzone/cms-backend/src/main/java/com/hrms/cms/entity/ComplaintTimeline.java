package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_TIMELINE", indexes = {
    @Index(name = "idx_timeline_complaint", columnList = "COMPLAINT_ID"),
    @Index(name = "idx_timeline_performed_at", columnList = "performedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_ID", nullable = false)
    private Long complaintId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 200)
    private String performedBy;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(length = 30)
    private String fromStatus;

    @Column(length = 30)
    private String toStatus;

    private LocalDateTime performedAt;

    // ═══ Automatic-vs-manual discriminator (UST848) ═══
    // Before this existed, the only way to tell a system event from a user action was to guess from
    // performedBy, which is written as "SYSTEM", "System" and "system" by different services — so
    // "show me only what the entity actually did" could not be answered reliably. Nullable rather
    // than NOT NULL because pre-existing rows have no recoverable answer; the migration backfills
    // them from performedBy and leaves genuinely ambiguous ones MANUAL.
    @Enumerated(EnumType.STRING)
    @Column(name = "event_source", length = 20)
    @Builder.Default
    private TimelineEventSource eventSource = TimelineEventSource.MANUAL;

    @PrePersist
    protected void onCreate() {
        this.performedAt = LocalDateTime.now();
        if (this.eventSource == null) {
            this.eventSource = TimelineEventSource.MANUAL;
        }
    }
}
