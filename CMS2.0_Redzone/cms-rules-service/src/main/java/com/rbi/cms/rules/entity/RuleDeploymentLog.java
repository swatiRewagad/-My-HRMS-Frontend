package com.rbi.cms.rules.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "RULE_DEPLOYMENT_LOG")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDeploymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false, length = 50)
    private String deploymentId;

    @Column(name = "rules_count")
    private Integer rulesCount;

    @Column(name = "deployed_by", nullable = false, length = 100)
    private String deployedBy;

    @Column(name = "deployed_at", updatable = false)
    private Instant deployedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @PrePersist
    public void prePersist() {
        if (deployedAt == null) deployedAt = Instant.now();
    }
}
