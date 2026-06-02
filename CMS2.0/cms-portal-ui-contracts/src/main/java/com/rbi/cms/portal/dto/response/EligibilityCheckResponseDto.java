package com.rbi.cms.portal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Eligibility evaluation result returned to the portal")
public class EligibilityCheckResponseDto {

    @Schema(description = "ELIGIBLE or NOT_ELIGIBLE")
    private String outcome;

    @Schema(description = "Machine-readable reason code", example = "COURT_MATTER_PENDING")
    private String reasonCode;

    @Schema(description = "Human-readable message for the user")
    private String standardMessage;

    @Schema(description = "PROCEED_TO_REGISTRATION or SHOW_ADVISORY")
    private String nextAction;

    @Schema(description = "Regulatory reference text")
    private String reference;
}
