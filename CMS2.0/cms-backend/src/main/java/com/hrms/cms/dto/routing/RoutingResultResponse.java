package com.hrms.cms.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where a complaint was routed on intake. Reports the decision only — this endpoint does not persist
 * it, so the complaint number is echoed rather than looked up.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingResultResponse {

    private String complaintNumber;
    private String department;
    private String assignedRole;
    private String stage;
    /** Set only when the decision defers to another department later in the flow. */
    private String targetDepartment;
    private String reason;
    private String routedAt;
}
