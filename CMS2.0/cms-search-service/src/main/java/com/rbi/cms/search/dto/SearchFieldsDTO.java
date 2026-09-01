package com.rbi.cms.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchFieldsDTO {

    private String complaintId;
    private String complaintNumber;
    private String assignedTo;
    private String slaBreachIn;
    private String mode;
    private String complainantName;
    private String status;
    private String entityName;
    private String complaintCategory;
    private String createdDate;
    private String lastUpdatedDate;
    private String priority;
    private String subject;
}
