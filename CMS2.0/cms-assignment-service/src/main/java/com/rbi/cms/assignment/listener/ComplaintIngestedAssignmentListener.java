package com.rbi.cms.assignment.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.assignment.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

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

            String assignedTo = assignmentService.assignComplaint(
                    event.getComplaintId(),
                    extractCategory(event.getPayload()),
                    extractPriority(event.getPayload()),
                    null
            );

            ComplaintEvent assignedEvent = ComplaintEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .complaintId(event.getComplaintId())
                    .previousStatus(event.getCurrentStatus())
                    .currentStatus(ComplaintStatus.ASSIGNED)
                    .assignedTo(assignedTo)
                    .occurredAt(Instant.now())
                    .correlationId(event.getCorrelationId())
                    .build();

            String payload = objectMapper.writeValueAsString(assignedEvent);
            kafkaTemplate.send(KafkaTopics.COMPLAINT_ASSIGNED, event.getComplaintId(), payload);

            ack.acknowledge();
            log.info("Complaint {} assigned to: {}", event.getComplaintId(), assignedTo);
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
