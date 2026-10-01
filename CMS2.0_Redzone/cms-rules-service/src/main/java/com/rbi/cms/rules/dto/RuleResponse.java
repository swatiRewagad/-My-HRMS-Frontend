package com.rbi.cms.rules.dto;

import com.rbi.cms.rules.entity.RuleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResponse {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String categoryCode;
    private String categoryName;
    private String drlContent;
    private Integer salience;
    private Integer version;
    private RuleStatus status;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private String approvedBy;
    private Instant approvedAt;
}
