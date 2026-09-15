package com.rbi.cms.rules.dto;

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
public class RuleTestResponse {

    private boolean executed;
    private int rulesFireCount;
    private Map<String, Object> outputFacts;
    private List<String> rulesFired;
    private String error;
}
