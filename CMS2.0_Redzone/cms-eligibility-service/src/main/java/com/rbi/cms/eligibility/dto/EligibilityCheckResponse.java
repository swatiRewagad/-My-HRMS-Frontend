package com.rbi.cms.eligibility.dto;

import com.rbi.cms.common.enums.EligibilityOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of eligibility evaluation")
public class EligibilityCheckResponse {

    @Schema(description = "Outcome: ELIGIBLE or NOT_ELIGIBLE")
    private EligibilityOutcome outcome;

    @Schema(description = "Reason code for the outcome", example = "COURT_MATTER_PENDING")
    private String reasonCode;

    @Schema(description = "Human-readable reason message")
    private String reasonMessage;

    @Schema(description = "Standard response/guidance for NOT_ELIGIBLE outcome")
    private String standardResponse;

    @Schema(description = "Next action: PROCEED_TO_REGISTRATION or SHOW_ADVISORY")
    private String nextAction;

    @Schema(description = "Reference/guidance text")
    private String reference;
}
