package com.hrms.cms.dto;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CategorizationResult {
    private Long categoryId;
    private String categoryName;
    private String matchedRule;
    private List<String> matchedKeywords;
    private String priority;
    private double confidence;
}
