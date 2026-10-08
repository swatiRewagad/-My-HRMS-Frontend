package com.hrms.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.AppealOutboxEvent;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.AppealOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes complaint lifecycle events to the transactional outbox.
 *
 * The row is written in the SAME transaction as the filing, so either both commit or neither does.
 * cms-outbox-publisher then polls OUTBOX_EVENT and publishes to Kafka with retries.
 *
 * This exists because the citizen filing path had no durable handoff at all. Its only Kafka path was
 * ComplaintEventPublisher's @Async kafkaTemplate.send: off-transaction, so it could fire before the
 * commit or after a rollback, and it swallowed broker failures. A broker outage therefore committed the
 * complaint row and lost the event permanently — the complaint reached its table and no RBIO or CEPC
 * officer was ever told it existed. Modelled on AaOutboxPublisher, which already does this correctly
 * for appeals against the same table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintOutboxPublisher {

    /** Matches the topic cms-assignment-service's ComplaintIngestedAssignmentListener consumes. */
    public static final String TOPIC_COMPLAINT_INGESTED = "complaint.ingested";
    public static final String AGGREGATE_TYPE_COMPLAINT = "COMPLAINT";
    public static final String EVENT_COMPLAINT_INGESTED = "COMPLAINT_INGESTED";

    private final AppealOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records the ingestion of {@code complaint} for downstream assignment. Never throws.
     *
     * <p>A serialisation failure is logged rather than propagated: a complaint the citizen correctly
     * filed must not be rejected because the handoff record could not be written. The log plus the
     * absent row make the gap visible to operations.
     */
    public void publishIngested(Complaint complaint) {
        if (complaint == null || complaint.getComplaintNumber() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("complaintNumber", complaint.getComplaintNumber());
            payload.put("department", complaint.getDepartment());
            payload.put("rbioOfficeCode", complaint.getRbioOfficeCode());
            payload.put("assignedRole", complaint.getAssignedRole());
            payload.put("assignedOfficer", complaint.getAssignedOfficer());
            payload.put("workflowStage", complaint.getWorkflowStage());
            payload.put("status", complaint.getStatus());
            payload.put("entityName", complaint.getEntityName());
            payload.put("entityCode", complaint.getEntityCode());
            payload.put("categoryId", complaint.getCategoryId());
            payload.put("categoryName", complaint.getCategoryName());
            payload.put("priority", complaint.getPriority());
            payload.put("filingType", complaint.getFilingType());
            payload.put("filedAt", complaint.getFiledAt() != null ? complaint.getFiledAt().toString() : null);
            // The complainant's name, phone, email and address are deliberately absent. This lands on a
            // Kafka topic any downstream consumer can read, and none of them need a citizen's PII to
            // route or assign the complaint — the complaint number is enough to look it up.

            outboxRepository.save(AppealOutboxEvent.builder()
                    .aggregateId(complaint.getComplaintNumber())
                    .aggregateType(AGGREGATE_TYPE_COMPLAINT)
                    .eventType(EVENT_COMPLAINT_INGESTED)
                    .topic(TOPIC_COMPLAINT_INGESTED)
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(AppealOutboxEvent.STATUS_PENDING)
                    .correlationId(complaint.getComplaintNumber())
                    .build());
        } catch (Exception e) {
            log.warn("Could not write outbox event {} for complaint {}: {}",
                    EVENT_COMPLAINT_INGESTED, complaint.getComplaintNumber(), e.getMessage());
        }
    }
}
