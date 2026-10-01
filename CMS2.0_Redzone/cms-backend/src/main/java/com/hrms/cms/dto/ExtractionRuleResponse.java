package com.hrms.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionRuleResponse {

    private Long id;
    private String ruleName;
    private String ruleCode;
    private String description;
    private String patternType;
    private String pattern;
    private String targetField;
    private Integer extractGroup;
    private String transform;
    private Integer priority;
    private Boolean isActive;
    private String sourceScope;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
