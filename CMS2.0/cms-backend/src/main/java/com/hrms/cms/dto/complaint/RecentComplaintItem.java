package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentComplaintItem {

    private String complaintNumber;
    private String subject;
    private String entityName;
    private String complainantName;
    private String status;
    private String date;
}
