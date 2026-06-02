package com.rbi.cms.assignment.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.assignment.service.AssignmentService;
import com.rbi.cms.common.dto.AssignmentFact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintIngestedAssignmentListener {

    private final AssignmentService assignmentService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.COMPLAINT_INGESTED, groupId = "cms-assignment-group")
    public void onComplaintIngested(String message, Acknowledgment ack) {
        try {
            ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
            log.info("Assignment listener received complaint.ingested: {}", event.getComplaintId());

            AssignmentFact result = assignmentService.assignComplaint(
                    event.getComplaintId(),
                    extractCategory(event.getPayload()),
                    extractPriority(event.getPayload()),
                    null
            );

            ComplaintEvent assignedEvent = ComplaintEvent.builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .complaintId(event.getComplaintId())
                    .previousStatus(event.getCurrentStatus())
                    .currentStatus(com.rbi.cms.common.enums.ComplaintStatus.ASSIGNED)
                    .assignedTo(result.getAssignedTeam())
                    .occurredAt(java.time.Instant.now())
                    .correlationId(event.getCorrelationId())
                    .build();

            String payload = objectMapper.writeValueAsString(assignedEvent);
            kafkaTemplate.send(KafkaTopics.COMPLAINT_ASSIGNED, event.getComplaintId(), payload);

            ack.acknowledge();
            log.info("Complaint {} assigned to team: {}", event.getComplaintId(), result.getAssignedTeam());
        } catch (Exception e) {
            log.error("Failed to process assignment for event: {}", message, e);
            throw new RuntimeException("Assignment processing failed", e);
        }
    }

    private String extractCategory(String payload) {
        try {
            var node = objectMapper.readTree(payload);
            return node.has("category") ? node.get("category").asText() : "GENERAL";
        } catch (Exception e) {
            return "GENERAL";
        }
    }

    private String extractPriority(String payload) {
        try {
            var node = objectMapper.readTree(payload);
            return node.has("priority") ? node.get("priority").asText() : "MEDIUM";
        } catch (Exception e) {
            return "MEDIUM";
        }
    }
}
