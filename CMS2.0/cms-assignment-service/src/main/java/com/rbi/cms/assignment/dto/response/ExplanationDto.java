package com.rbi.cms.assignment.dto.response;

import java.time.Instant;

public record ExplanationDto(
    Long matchedRuleId,
    String matchedRuleCode,
    String matchedRuleName,
    int ruleSetVersion,
    String hitPolicy,
    String distributionStrategy,
    Integer candidatesConsidered,
    Integer candidatesExcluded,
    String exclusionReasons,
    boolean fallbackApplied,
    String fallbackReason,
    Instant evaluatedAt,
    long latencyMs
) {}
