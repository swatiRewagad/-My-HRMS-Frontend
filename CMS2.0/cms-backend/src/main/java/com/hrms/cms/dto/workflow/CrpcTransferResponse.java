package com.hrms.cms.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrpcTransferResponse {

    private String complaintId;
    private String complaintNumber;
    private String from;
    /** Age of the transfer in whole days. */
    private long pending;
    private String fromOffice;
    private String targetOffice;
    private String status;
    private String entityName;
    private String proposedCategory;
    private String creationDate;

    // Not yet sourced from anywhere. Kept so the transfer grid's columns stay bound.
    @Builder.Default private String language = "";
    @Builder.Default private String territory = "";
    @Builder.Default private List<Object> timeline = List.of();

    private String subject;
    private String complainantName;
    private String complainantEmail;
    private String complainantPhone;
    private String description;
}
