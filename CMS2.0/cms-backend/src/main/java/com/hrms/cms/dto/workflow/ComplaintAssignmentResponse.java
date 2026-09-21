package com.hrms.cms.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintAssignmentResponse {

    private String complaintNumber;
    private String assignedTo;
    private String role;
}
