package com.hrms.cms.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CategorizationRuleRequest {
    private String ruleName;
    private String keywords;
    private Long categoryId;
    private String priority;
    private String status;
    private String source;
    private String description;
    private Integer ruleOrder;
}
