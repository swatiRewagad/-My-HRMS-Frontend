package com.hrms.cms.dto.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code band}/{@code maxAllowed} are present only when valid, {@code reason} only when invalid —
 * hence NON_NULL, which keeps each branch emitting the same keys it did as a Map.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AwardValidationResponse {

    /** Echoed back as the caller supplied it, so an unparseable amount is still visible. */
    private String amount;
    private String compensationType;
    private boolean valid;
    private String band;
    private String maxAllowed;
    private String reason;
}
