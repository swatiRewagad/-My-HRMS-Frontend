package com.rbi.cms.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficerTaskResponse {
    private String complaintId;
    private String category;
    private String priority;
    private String status;
    private String subject;
    private String complainantName;
    private String entityName;
    private String assignedTeam;
    private String assignedTo;
    private Double amountInvolved;
    private Instant createdAt;
    private Instant slaDueDate;
    private int slaPercentage;
}
