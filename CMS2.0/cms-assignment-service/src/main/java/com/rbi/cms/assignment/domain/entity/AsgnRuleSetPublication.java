package com.rbi.cms.assignment.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ASGN_RULE_SET_PUBLICATION")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnRuleSetPublication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "DECISION_POINT", nullable = false, length = 100)
    private String decisionPoint;

    @Column(name = "ACTIVE_VERSION_ID", nullable = false)
    private Long activeVersionId;

    @Column(name = "PUBLISHED_AT", nullable = false)
    private Instant publishedAt;
}
