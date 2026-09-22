package com.rbi.cms.assignment.service;

import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.common.dto.AssignmentFact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final KieContainer kieContainer;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public AssignmentFact assignComplaint(String complaintId, String category, String priority, Double amount) {
        log.info("Assigning complaint: {} category: {} priority: {}", complaintId, category, priority);

        AssignmentFact fact = AssignmentFact.builder()
                .complaintId(complaintId)
                .category(category)
                .priority(priority)
                .amountInvolved(amount)
                .build();

        KieSession kieSession = kieContainer.newKieSession();
        try {
            kieSession.insert(fact);
            kieSession.fireAllRules();
        } finally {
            kieSession.dispose();
        }

        log.info("Complaint {} assigned to team: {}, officer: {}, escalated: {}",
                complaintId, fact.getAssignedTeam(), fact.getAssignedOfficer(), fact.isEscalated());

        return fact;
    }

    public void reassign(String complaintId, String newTeam, String newOfficer, String reason) {
        log.info("Reassigning complaint {} to team: {}, officer: {}, reason: {}",
                complaintId, newTeam, newOfficer, reason);
    }
}
