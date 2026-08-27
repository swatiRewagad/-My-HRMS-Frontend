package com.rbi.cms.assignment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    // Placeholder — will be replaced by AssignmentResolveService in Phase 1
    // Kept to satisfy the Kafka listener compilation during scaffolding

    public String assignComplaint(String complaintId, String category, String priority, Double amount) {
        log.info("Assignment requested for complaint: {} category: {} priority: {}", complaintId, category, priority);
        // TODO: Phase 1 — delegate to RuleEvaluator
        return "GENERAL_INTAKE_POOL";
    }
}
