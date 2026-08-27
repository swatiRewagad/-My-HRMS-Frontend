package com.rbi.cms.assignment.dto.request;

import java.util.List;
import java.util.Map;

public record RuleBulkSaveRequest(
    List<RuleDto> rules,
    DefaultOutcomeDto defaultOutcome
) {
    public record RuleDto(
        Long id,
        String ruleCode,
        String name,
        String description,
        int priority,
        int rowOrder,
        boolean enabled,
        List<ConditionDto> conditions,
        OutcomeDto outcome
    ) {}

    public record ConditionDto(
        String attributeCode,
        String operator,
        String valueText,
        String valueNumFrom,
        String valueNumTo,
        String valueDateFrom,
        String valueDateTo,
        String valueList
    ) {}

    public record OutcomeDto(
        String outcomeType,
        String targetId,
        String assignMode,
        String distributionStrategy,
        Integer chainOrder,
        String orgUnitFromAttribute
    ) {}

    public record DefaultOutcomeDto(
        String outcomeType,
        String targetId,
        String assignMode,
        String distributionStrategy
    ) {}
}
