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

    @PrePersist
    protected void onCreate() {
        this.performedAt = LocalDateTime.now();
    }
}
