package com.hrms.cms.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where a complaint goes when CRPC hands it on for approval. Distinct from
 * {@link RoutingResultResponse} because the decision has no target department at this point — the
 * department in hand <em>is</em> the destination — and because the timestamp names a different event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalForwardResponse {

    private String complaintNumber;
    private String department;
    private String assignedRole;
    private String stage;
    private String reason;
    private String forwardedAt;
}
