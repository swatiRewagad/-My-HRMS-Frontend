package com.rbi.cms.assignment.dto.response;

public record OutcomeDto(
    String type,
    String targetId,
    String assignedUserId,
    String assignMode,
    String distributionStrategy
) {}
