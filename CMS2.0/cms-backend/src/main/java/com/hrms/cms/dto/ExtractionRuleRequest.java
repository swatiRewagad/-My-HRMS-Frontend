package com.hrms.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionRuleRequest {

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    private String ruleCode;

    private String description;

    @NotBlank(message = "Pattern type is required")
    private String patternType; // REGEX or KEYWORD_LIST

    @NotBlank(message = "Pattern is required")
    private String pattern;

    @NotBlank(message = "Target field is required")
    private String targetField;

    private Integer extractGroup;

    private String transform;

    @NotNull(message = "Priority is required")
    private Integer priority;

    private Boolean isActive;

    private String sourceScope; // BODY, SUBJECT, BOTH
}
