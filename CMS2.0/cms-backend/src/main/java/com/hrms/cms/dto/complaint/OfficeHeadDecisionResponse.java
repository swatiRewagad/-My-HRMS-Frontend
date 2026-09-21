package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficeHeadDecisionResponse {

    private String complaintNumber;
    private String status;
    private String assignedOfficer;
    private String decision;
}
