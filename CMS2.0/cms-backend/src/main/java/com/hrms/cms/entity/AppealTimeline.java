package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "APPEAL_TIMELINE", indexes = {
    @Index(name = "idx_appeal_timeline_number", columnList = "appealNumber"),
    @Index(name = "idx_appeal_timeline_performed_at", columnList = "performedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppealTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String appealNumber;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 200)
    private String performedBy;

    /**
     * The actor's name as it read when the action happened — the point-in-time counterpart to
     * {@link #getPerformedBy()}. See {@link ComplaintTimeline#getPerformedByName()} for why this is
     * captured rather than joined at read time. Null when the actor has no officer-pool entry.
     */
    @Column(length = 250)
    private String performedByName;

    @Column(length = 50)
    private String performedByRole;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(length = 30)
    private String fromStatus;

    @Column(length = 30)
    private String toStatus;

    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        this.performedAt = LocalDateTime.now();
    }

    /** See {@link ComplaintTimeline#displayActor()}. */
    public String displayActor() {
        return performedByName != null && !performedByName.isBlank() ? performedByName : performedBy;
    }
}
