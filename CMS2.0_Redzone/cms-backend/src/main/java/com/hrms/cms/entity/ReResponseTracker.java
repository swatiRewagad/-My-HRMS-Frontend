package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "RE_RESPONSE_TRACKER", indexes = {
    @Index(name = "idx_re_tracker_complaint", columnList = "complaintId"),
    @Index(name = "idx_re_tracker_re", columnList = "regulatedEntityId"),
    @Index(name = "idx_re_tracker_breached", columnList = "breached"),
    @Index(name = "idx_re_tracker_forwarded", columnList = "forwardedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReResponseTracker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long complaintId;

    @Column(nullable = false)
    private Long regulatedEntityId;

    @Column(nullable = false)
    private LocalDateTime forwardedAt;

    private LocalDateTime respondedAt;

    @Column(nullable = false)
    private int windowDays;

    private LocalDateTime windowExpiresAt;

    @Column(nullable = false)
    private boolean breached;

    private boolean exParteEligible;

    @Column(length = 500)
    private String notes;

    // ═══ Response & query fields (RE portal) ═══
    @Column(columnDefinition = "TEXT")
    private String responseText;

    @Column(columnDefinition = "TEXT")
    private String queryText;

    private LocalDateTime queryRaisedAt;

    @Builder.Default
    private Boolean extensionGranted = false;

    private Integer extensionDays;

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
