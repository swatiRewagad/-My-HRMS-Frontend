package com.hrms.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionTestResponse {

    private Map<String, String> extractedFields;
    private List<MatchDetail> matchDetails;
    private List<String> unmatchedFields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchDetail {
        private String ruleName;
        private String ruleCode;
        private String targetField;
        private String extractedValue;
        private Integer priority;
        private int matchStart;
        private int matchEnd;
    }
}
