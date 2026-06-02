package com.hrms.cms.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpdateComplaintRequest {
    private String status;
    private String priority;
    private String assignedOfficer;
    private String remarks;
}
