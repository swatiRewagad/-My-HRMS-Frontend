package com.hrms.cms.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.Complaint;
import com.rbi.cms.common.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // fallbackExecution: the CRPC email-intake path saves outside any transaction, so by the time it
    // raises this event the row is already committed and the event must fire immediately, not be dropped.
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onComplaintCreated(ComplaintCreatedEvent event) {
        publishEvent(KafkaTopics.COMPLAINT_INGESTED, event.complaint(), null, "NEW", null);
    }

    @Async("taskExecutor")
    public void publishComplaintAssigned(Complaint complaint, String actor) {
        publishEvent(KafkaTopics.COMPLAINT_ASSIGNED, complaint, null, "ASSIGNED", actor);
    }

    @Async("taskExecutor")
    public void publishComplaintClosed(Complaint complaint, String actor, String prevStatus) {
        publishEvent(KafkaTopics.COMPLAINT_CLOSED, complaint, prevStatus, complaint.getStatus().toUpperCase(), actor);
    }

    @Async("taskExecutor")
    public void publishComplaintEscalated(Complaint complaint, String actor, String prevStatus) {
        publishEvent(KafkaTopics.COMPLAINT_ESCALATED, complaint, prevStatus, "ESCALATED", actor);
    }

    /**
     * Announces that {@code readBy} has opened the complaint, so the search index can clear its unread
     * marker for that officer alone. Deliberately carries no status: the only consumer appends to a
     * list, and a stored status with no {@code ComplaintStatus} constant would otherwise get the whole
     * event dropped.
     */
    @Async("taskExecutor")
    public void publishComplaintRead(String complaintNumber, String readBy) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("complaintId", complaintNumber);
            event.put("readBy", readBy);
            event.put("occurredAt", Instant.now().toString());
            event.put("correlationId", UUID.randomUUID().toString());
            send(KafkaTopics.COMPLAINT_READ, complaintNumber, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Error publishing {} event: {}", KafkaTopics.COMPLAINT_READ, e.getMessage(), e);
        }
    }

    private void publishEvent(String topic, Complaint complaint, String prevStatus, String currentStatus, String actor) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("complaintId", complaint.getComplaintNumber());
            event.put("previousStatus", prevStatus);
            event.put("currentStatus", currentStatus);
            // Top-level, not only inside the payload: consumers applying a partial update read these
            // fields directly, and department is the tenancy boundary the search index filters on, so
            // a reassignment or transfer must travel with every event rather than only a full rewrite.
            event.put("department", complaint.getDepartment());
            event.put("assignedTo", complaint.getAssignedOfficer());
            event.put("regionalOffice", complaint.getRegionalOffice());
            event.put("occurredAt", Instant.now().toString());
            event.put("correlationId", UUID.randomUUID().toString());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("complaintNumber", complaint.getComplaintNumber());
            payload.put("subject", complaint.getSubject());
            payload.put("priority", complaint.getPriority());
            payload.put("category", "GENERAL");
            payload.put("bankId", complaint.getBankId());
            payload.put("complainantName", complaint.getComplainantName());
            // cms-notification-service dispatches the complainant's acknowledgement; without contact
            // details on the event it has no addressee and no DB of its own to look one up in.
            payload.put("complainantEmail", complaint.getComplainantEmail());
            payload.put("complainantPhone", complaint.getComplainantPhone());
            payload.put("channel", complaint.getFilingType());
            payload.put("filingType", complaint.getFilingType());
            payload.put("entityCode", complaint.getEntityCode() != null ? complaint.getEntityCode() : "");
            payload.put("department", complaint.getDepartment());
            payload.put("assignedOfficer", complaint.getAssignedOfficer());
            payload.put("assignedRole", complaint.getAssignedRole());
            payload.put("regionalOffice", complaint.getRegionalOffice());
            payload.put("createdBy", complaint.getCreatedBy());
            payload.put("assignedOfficerName", complaint.getAssignedOfficerName());
            if (actor != null) {
                payload.put("actor", actor);
            }
            event.put("payload", objectMapper.writeValueAsString(payload));

            send(topic, complaint.getComplaintNumber(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Error publishing {} event: {}", topic, e.getMessage(), e);
        }
    }

    private void send(String topic, String complaintNumber, String message) {
        kafkaTemplate.send(topic, complaintNumber, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for {}: {}", topic, complaintNumber, ex.getMessage());
                    } else {
                        log.info("Published {} for {} to partition {}",
                                topic, complaintNumber, result.getRecordMetadata().partition());
                    }
                });
    }
}
