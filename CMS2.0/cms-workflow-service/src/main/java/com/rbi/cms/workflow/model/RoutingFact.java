package com.rbi.cms.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingFact {
    private String complaintId;
    private String channel;
    private String category;
    private String priority;
    private String entityName;
    private String entityType;
    private String entitySize;
    private String department;
    private String assignedDeo;
    private String assignedReviewer;
    private String assignedOfficer;
    private String assignedSupervisor;
    private String assignedConciliator;
    private String assignedAdjudicator;
    private String regionalOffice;
}
