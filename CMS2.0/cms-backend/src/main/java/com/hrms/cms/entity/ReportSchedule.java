package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "REPORT_SCHEDULES", indexes = {
    @Index(name = "idx_schedule_slot", columnList = "deliverySlot"),
    @Index(name = "idx_schedule_active", columnList = "active"),
    @Index(name = "idx_schedule_owner", columnList = "ownerUsername")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReportSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reportDefinitionId;

    @Column(nullable = false, length = 200)
    private String ownerUsername;

    @Column(nullable = false, length = 200)
    private String recipientEmail;

    @Column(nullable = false, length = 20)
    private String frequency;

    @Column(nullable = false, length = 10)
    private String deliverySlot;

    private boolean active;

    private LocalDateTime lastSentAt;
    private LocalDateTime nextScheduledAt;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }
}
