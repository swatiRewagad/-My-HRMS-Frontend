package com.rbi.cms.assignment.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ASGN_RULE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "VERSION_ID", nullable = false)
    private Long versionId;

    @Column(name = "RULE_CODE", nullable = false, length = 50)
    private String ruleCode;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "PRIORITY", nullable = false)
    private Integer priority;

    @Column(name = "ROW_ORDER", nullable = false)
    private Integer rowOrder;

    @Column(name = "ENABLED")
    private boolean enabled;

    @Column(name = "EFFECTIVE_FROM")
    private Instant effectiveFrom;

    @Column(name = "EFFECTIVE_TO")
    private Instant effectiveTo;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (!enabled && id == null) enabled = true;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
