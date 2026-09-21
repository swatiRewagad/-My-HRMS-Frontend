package com.hrms.cms.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosureStatusResponse {

    private String complaintNumber;
    private boolean hasEmail;
    /** Null until the closure letter is dispatched; the UI keys "not yet sent" off its absence. */
    private String closureLetterSentAt;
    private String status;
    private String closureCause;
    private String closureClause;
    private String customClosureText;
}
