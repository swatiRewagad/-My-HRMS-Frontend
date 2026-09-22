package com.rbi.cms.workflow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("dev-local")
public class NoOpWorkflowEventPublisher implements WorkflowEventPublisher {

    @Override
    public void publishAssigned(String complaintId, String department, String officer) {
        log.debug("[DEV-LOCAL] Skipped publishAssigned: complaint={}, dept={}", complaintId, department);
    }

    @Override
    public void publishInProgress(String complaintId, String officer) {
        log.debug("[DEV-LOCAL] Skipped publishInProgress: complaint={}", complaintId);
    }

    @Override
    public void publishEscalated(String complaintId, String reason, String escalatedTo) {
        log.debug("[DEV-LOCAL] Skipped publishEscalated: complaint={}", complaintId);
    }

    @Override
    public void publishResolved(String complaintId, String resolutionSummary) {
        log.debug("[DEV-LOCAL] Skipped publishResolved: complaint={}", complaintId);
    }

    @Override
    public void publishClosed(String complaintId) {
        log.debug("[DEV-LOCAL] Skipped publishClosed: complaint={}", complaintId);
    }
}
