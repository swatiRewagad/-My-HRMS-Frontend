package com.rbi.cms.ingestion.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDraftUpdateRequest {

    private String complainantName;
    private String complainantPhone;
    private String cpgramsNumber;
    private String complaintSummary;
    private String category;
    private String subject;
    private String body;
    private String entityName;
    private String entityType;
}
