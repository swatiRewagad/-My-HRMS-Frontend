package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "REPORT_DEFINITIONS", indexes = {
    @Index(name = "idx_report_owner", columnList = "ownerUsername"),
    @Index(name = "idx_report_type", columnList = "chartType")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReportDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String ownerUsername;

    @Column(nullable = false, length = 500)
    private String sentence;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String queryDefinition;

    @Column(length = 30)
    private String chartType;

    @Column(length = 200)
    private String title;

    private boolean dashboardWidget;

    private int displayOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
