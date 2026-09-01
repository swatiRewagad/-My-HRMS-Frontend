package com.rbi.cms.search.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintRowDTO {

    private Long complaintId;
    private String complaintNumber;
    private String assignedTo;
    private String slaBreachIn;
    private String mode;
    private String complainantName;
    private String status;
    private String entityName;
    private String complaintCategory;
    private LocalDate createdDate;
    private LocalDate lastUpdatedDate;
    private String priority;
    private String subject;
    private String nodalOfficer;
    private String principalNodalOfficer;
    private String complaintColor;
    private Boolean isRead;
    private String slaColor;
}
