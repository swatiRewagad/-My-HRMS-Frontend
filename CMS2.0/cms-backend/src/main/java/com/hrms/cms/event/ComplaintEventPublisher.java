package com.hrms.cms.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.Complaint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintEventPublisher {

    private static final String TOPIC_COMPLAINT_INGESTED = "complaint.ingested";
    private static final String TOPIC_COMPLAINT_ASSIGNED = "complaint.assigned";
    private static final String TOPIC_COMPLAINT_CLOSED = "complaint.closed";
    private static final String TOPIC_COMPLAINT_ESCALATED = "complaint.escalated";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Async("taskExecutor")
    public void publishComplaintIngested(Complaint complaint) {
        publishEvent(TOPIC_COMPLAINT_INGESTED, complaint, null, "NEW", null);
    }

    @Async("taskExecutor")
    public void publishComplaintAssigned(Complaint complaint, String actor) {
        publishEvent(TOPIC_COMPLAINT_ASSIGNED, complaint, null, "ASSIGNED", actor);
    }

    @Async("taskExecutor")
    public void publishComplaintClosed(Complaint complaint, String actor, String prevStatus) {
        publishEvent(TOPIC_COMPLAINT_CLOSED, complaint, prevStatus, complaint.getStatus().toUpperCase(), actor);
    }

    @Async("taskExecutor")
    public void publishComplaintEscalated(Complaint complaint, String actor, String prevStatus) {
        publishEvent(TOPIC_COMPLAINT_ESCALATED, complaint, prevStatus, "ESCALATED", actor);
    }

    private void publishEvent(String topic, Complaint complaint, String prevStatus, String currentStatus, String actor) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("complaintId", complaint.getComplaintNumber());
            event.put("previousStatus", prevStatus);
            event.put("currentStatus", currentStatus);
            event.put("occurredAt", Instant.now().toString());
            event.put("correlationId", UUID.randomUUID().toString());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("subject", complaint.getSubject());
            payload.put("priority", complaint.getPriority());
            payload.put("category", "GENERAL");
            payload.put("bankId", complaint.getBankId());
            payload.put("complainantName", complaint.getComplainantName());
            payload.put("channel", complaint.getFilingType());
            payload.put("filingType", complaint.getFilingType());
            payload.put("entityCode", complaint.getEntityCode() != null ? complaint.getEntityCode() : "");
            payload.put("department", complaint.getDepartment());
            payload.put("assignedOfficer", complaint.getAssignedOfficer());
            payload.put("assignedRole", complaint.getAssignedRole());
            if (actor != null) {
                payload.put("actor", actor);
            }
            event.put("payload", objectMapper.writeValueAsString(payload));

            String message = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(topic, complaint.getComplaintNumber(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} for {}: {}",
                                    topic, complaint.getComplaintNumber(), ex.getMessage());
                        } else {
                            log.info("Published {} for {} to partition {}",
                                    topic, complaint.getComplaintNumber(), result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing {} event: {}", topic, e.getMessage(), e);
        }
    }
}
