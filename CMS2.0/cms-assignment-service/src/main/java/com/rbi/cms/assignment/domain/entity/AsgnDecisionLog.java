package com.rbi.cms.assignment.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ASGN_DECISION_LOG")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "CORRELATION_ID", length = 100)
    private String correlationId;

    @Column(name = "DECISION_POINT", nullable = false, length = 100)
    private String decisionPoint;

    @Column(name = "CASE_REF", length = 100)
    private String caseRef;

    @Column(name = "RULE_SET_VERSION_ID")
    private Long ruleSetVersionId;

    @Column(name = "MATCHED_RULE_ID")
    private Long matchedRuleId;

    @Column(name = "MATCHED_RULE_CODE", length = 50)
    private String matchedRuleCode;

    @Column(name = "OUTCOME_TYPE", length = 30)
    private String outcomeType;

    @Column(name = "OUTCOME_TARGET", length = 200)
    private String outcomeTarget;

    @Column(name = "ASSIGNED_USER_ID", length = 100)
    private String assignedUserId;

    @Column(name = "DISTRIBUTION_STRATEGY", length = 30)
    private String distributionStrategy;

    @Column(name = "CANDIDATES_CONSIDERED")
    private Integer candidatesConsidered;

    @Column(name = "CANDIDATES_EXCLUDED")
    private Integer candidatesExcluded;

    @Column(name = "FALLBACK_APPLIED")
    private boolean fallbackApplied;

    @Column(name = "FALLBACK_REASON", length = 500)
    private String fallbackReason;

    @Column(name = "LATENCY_MS")
    private Integer latencyMs;

    @Lob
    @Column(name = "CONTEXT_JSON")
    private String contextJson;

    @Column(name = "DRY_RUN")
    private boolean dryRun;

    @Column(name = "CALLER_SERVICE", length = 100)
    private String callerService;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
