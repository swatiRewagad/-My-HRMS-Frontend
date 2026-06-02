package com.rbi.cms.eligibility.dto;

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
@Schema(description = "Request to check eligibility based on questionnaire answers")
public class EligibilityCheckRequest {

    @NotNull(message = "Channel is required")
    @Schema(description = "Channel through which the check is being performed", example = "WEB_PORTAL")
    private String channel;

    @NotEmpty(message = "Answers are required")
    @Schema(description = "Map of question code to answer value")
    private Map<String, String> answers;

    @Schema(description = "Session identifier for audit trail")
    private String sessionId;

    @Schema(description = "IP address of the requester")
    private String ipAddress;
}
