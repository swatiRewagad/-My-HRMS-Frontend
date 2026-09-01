package com.rbi.cms.assignment.engine.compiler;

import com.rbi.cms.assignment.domain.enums.AssignMode;
import com.rbi.cms.assignment.domain.enums.DistributionStrategy;
import com.rbi.cms.assignment.domain.enums.OutcomeType;

public record CompiledOutcome(
    OutcomeType outcomeType,
    String targetId,
    AssignMode assignMode,
    DistributionStrategy distributionStrategy,
    Integer chainOrder,
    String orgUnitFromAttribute
) {}
