package com.rbi.cms.rules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleRequest {

    @NotBlank(message = "Rule code is required")
    private String ruleCode;

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    @NotBlank(message = "Category code is required")
    private String categoryCode;

    @NotBlank(message = "DRL content is required")
    private String drlContent;

    private Integer salience;

    private Instant effectiveFrom;

    private Instant effectiveTo;

    private String changeReason;
}
