package com.rbi.cms.ingestion.email.dto;

import com.rbi.cms.ingestion.email.entity.EmailIgnoreList.PatternType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IgnoreListRequest {

    @NotBlank(message = "Email pattern is required")
    private String emailPattern;

    private PatternType patternType;

    private String reason;
}
