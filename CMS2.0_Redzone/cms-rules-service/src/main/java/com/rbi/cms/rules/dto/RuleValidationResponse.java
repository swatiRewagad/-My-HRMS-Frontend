package com.rbi.cms.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleValidationResponse {

    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
}
