package com.rbi.cms.portal.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(description = "Portal eligibility check request")
public class EligibilityCheckRequestDto {

    @NotNull(message = "Channel is required")
    @Schema(description = "Source channel", example = "WEB_PORTAL")
    private String channel;

    @NotEmpty(message = "Answers map cannot be empty")
    @Schema(description = "Map of questionCode → user answer")
    private Map<String, String> answers;

    @Schema(description = "Browser session ID for audit")
    private String sessionId;
}
