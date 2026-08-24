package com.rbi.cms.assignment.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ASGN_EXCLUSION_RULE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnExclusionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "EXCLUSION_TYPE", nullable = false, length = 50)
    private String exclusionType;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Lob
    @Column(name = "CONDITION_JSON")
    private String conditionJson;

    @Column(name = "ACTIVE")
    private boolean active;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
