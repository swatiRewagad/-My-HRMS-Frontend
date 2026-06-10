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

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Async("taskExecutor")
    public void publishComplaintIngested(Complaint complaint) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("complaintId", complaint.getComplaintNumber());
            event.put("previousStatus", null);
            event.put("currentStatus", "NEW");
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
            event.put("payload", objectMapper.writeValueAsString(payload));

            String message = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(TOPIC_COMPLAINT_INGESTED, complaint.getComplaintNumber(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish complaint.ingested for {}: {}",
                                    complaint.getComplaintNumber(), ex.getMessage());
                        } else {
                            log.info("Published complaint.ingested for {} to partition {}",
                                    complaint.getComplaintNumber(), result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing complaint event: {}", e.getMessage(), e);
        }
    }
}
