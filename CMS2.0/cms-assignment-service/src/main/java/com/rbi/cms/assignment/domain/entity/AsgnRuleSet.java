package com.rbi.cms.assignment.domain.entity;

import com.rbi.cms.assignment.domain.enums.HitPolicy;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ASGN_RULE_SET")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnRuleSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "DECISION_POINT", nullable = false, length = 100)
    private String decisionPoint;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "HIT_POLICY", nullable = false, length = 30)
    private HitPolicy hitPolicy;

    @Column(name = "ACTIVE")
    private boolean active;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (!active && id == null) active = true;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
