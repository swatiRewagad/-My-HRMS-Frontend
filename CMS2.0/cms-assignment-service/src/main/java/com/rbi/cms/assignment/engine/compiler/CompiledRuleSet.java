package com.rbi.cms.assignment.engine.compiler;

import com.rbi.cms.assignment.domain.enums.HitPolicy;

import java.util.List;

public record CompiledRuleSet(
    Long ruleSetId,
    Long versionId,
    int versionNo,
    String decisionPoint,
    String tenantId,
    HitPolicy hitPolicy,
    List<CompiledRule> rules,
    CompiledOutcome defaultOutcome
) {}
