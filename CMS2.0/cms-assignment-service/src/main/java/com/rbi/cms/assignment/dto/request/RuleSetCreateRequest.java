package com.rbi.cms.assignment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RuleSetCreateRequest(
    @NotBlank String decisionPoint,
    @NotBlank String name,
    String description,
    String hitPolicy
) {}
