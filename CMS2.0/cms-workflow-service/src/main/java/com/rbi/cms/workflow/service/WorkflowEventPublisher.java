package com.rbi.cms.workflow.service;

public interface WorkflowEventPublisher {

    void publishAssigned(String complaintId, String department, String officer);

    void publishInProgress(String complaintId, String officer);

    void publishEscalated(String complaintId, String reason, String escalatedTo);

    void publishResolved(String complaintId, String resolutionSummary);

    void publishClosed(String complaintId);
}
