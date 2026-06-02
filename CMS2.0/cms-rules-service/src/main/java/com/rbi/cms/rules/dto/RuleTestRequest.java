package com.rbi.cms.rules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTestRequest {

    @NotBlank(message = "Category code is required")
    private String categoryCode;

    @NotNull(message = "Input facts are required")
    private Map<String, Object> inputFacts;
}
