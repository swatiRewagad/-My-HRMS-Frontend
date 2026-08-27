package com.rbi.cms.assignment.domain.entity;

import com.rbi.cms.assignment.domain.enums.AssignMode;
import com.rbi.cms.assignment.domain.enums.DistributionStrategy;
import com.rbi.cms.assignment.domain.enums.OutcomeType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ASGN_RULE_OUTCOME")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnRuleOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "RULE_ID")
    private Long ruleId;

    @Column(name = "VERSION_ID")
    private Long versionId;

    @Column(name = "IS_DEFAULT")
    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(name = "OUTCOME_TYPE", nullable = false, length = 30)
    private OutcomeType outcomeType;

    @Column(name = "TARGET_ID", nullable = false, length = 200)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ASSIGN_MODE", length = 20)
    private AssignMode assignMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "DISTRIBUTION_STRATEGY", length = 30)
    private DistributionStrategy distributionStrategy;

    @Column(name = "CHAIN_ORDER")
    private Integer chainOrder;

    @Column(name = "ORG_UNIT_FROM_ATTRIBUTE", length = 100)
    private String orgUnitFromAttribute;
}
