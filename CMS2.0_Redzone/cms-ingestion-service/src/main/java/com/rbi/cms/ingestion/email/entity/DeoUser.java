package com.rbi.cms.ingestion.email.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "DEO_USER")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeoUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_ID", unique = true, nullable = false, length = 100)
    private String userId;

    @Column(name = "DISPLAY_NAME", nullable = false, length = 200)
    private String displayName;

    @Column(name = "EMAIL", length = 200)
    private String email;

    @Column(name = "IS_ACTIVE")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "IS_ON_LEAVE")
    @Builder.Default
    private Boolean isOnLeave = false;

    @Column(name = "MAX_THRESHOLD")
    @Builder.Default
    private Integer maxThreshold = 10;

    @Column(name = "CURRENT_ASSIGNED_COUNT")
    @Builder.Default
    private Integer currentAssignedCount = 0;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isEligible() {
        return Boolean.TRUE.equals(isActive)
                && !Boolean.TRUE.equals(isOnLeave)
                && currentAssignedCount < maxThreshold;
    }
}
