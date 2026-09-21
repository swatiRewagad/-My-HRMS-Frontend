package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbioReassignResponse {

    private String complaintNumber;
    private String status;
    private String assignedTo;
    private String assignedToName;
    private String assignedRole;
    private String previousRole;
    private String previousOfficer;
    /** True when the reassignment also moved the complaint up a rung rather than sideways. */
    private boolean escalation;
}
