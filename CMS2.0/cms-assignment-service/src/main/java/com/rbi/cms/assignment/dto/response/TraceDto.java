package com.rbi.cms.assignment.dto.response;

public record TraceDto(
    Long ruleId,
    String ruleCode,
    String ruleName,
    boolean matched,
    String firstFailedAttribute,
    String failReason
) {}
