package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForwardComplaintResponse {

    private String complaintNumber;
    private String status;
    private String assignedTo;
    private String assignedToName;
    private String assignedRole;
    private String target;
}
