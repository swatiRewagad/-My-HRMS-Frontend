package com.rbi.cms.assignment.engine.compiler;

import java.util.List;

public record CompiledRule(
    Long ruleId,
    String ruleCode,
    String name,
    int priority,
    int rowOrder,
    List<CompiledCondition> conditions,
    CompiledOutcome outcome,
    int wildcardCount
) {
    public int specificity() {
        return conditions.size();
    }
}
