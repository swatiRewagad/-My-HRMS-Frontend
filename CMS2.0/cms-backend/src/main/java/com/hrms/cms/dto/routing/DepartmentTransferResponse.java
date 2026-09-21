package com.hrms.cms.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A CEPC ↔ RBIO handover. Reports both ends of the move, unlike the other routing payloads, because
 * the point of a transfer is the pair.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentTransferResponse {

    private String complaintNumber;
    /** Echoed from the request — the service does not verify the complaint was actually there. */
    private String fromDepartment;
    /** Taken from the routing decision, not the request, so a rejected target shows up here. */
    private String toDepartment;
    private String assignedRole;
    private String stage;
    private String reason;
    private String transferredAt;
}
