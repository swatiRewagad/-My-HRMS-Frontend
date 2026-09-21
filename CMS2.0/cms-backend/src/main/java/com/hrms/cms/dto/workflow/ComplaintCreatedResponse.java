package com.hrms.cms.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintCreatedResponse {

    private String complaintNumber;
    private Long complaintId;
    private String status;
    private String department;
}
