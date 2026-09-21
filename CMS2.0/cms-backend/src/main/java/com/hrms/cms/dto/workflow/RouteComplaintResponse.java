package com.hrms.cms.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteComplaintResponse {

    private String complaintNumber;
    private String department;
    private String assignedRole;
    private String assignedOfficer;
    private String status;
}
