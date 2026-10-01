package com.rbi.cms.search.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.search.index.LanguageDetector;
import com.rbi.cms.search.service.ComplaintSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.search.enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintIndexingListener {

    /**
     * Fields the strict mapping accepts from an event payload. Anything else is dropped.
     *
     * Necessary because the mapping is {@code dynamic: strict} and the payload is a free-form JSON
     * blob written by a different service. Passing it through verbatim, as the previous code did,
     * would now fail the whole write the moment a producer added a field — so the listener filters to
     * the contract instead of trusting the producer.
     */
    private static final Map<String, String> PAYLOAD_FIELDS = Map.ofEntries(
            Map.entry("complaintNumber", "complaintNumber"),
            Map.entry("subject", "subject"),
            Map.entry("description", "description"),
            Map.entry("advisoryText", "advisoryText"),
            Map.entry("closureClause", "closureClause"),
            Map.entry("withdrawalReason", "withdrawalReason"),
            Map.entry("schemeCoverageReason", "schemeCoverageReason"),
            Map.entry("reopenReason", "reopenReason"),
            Map.entry("reopenJustification", "reopenJustification"),
            Map.entry("categoryId", "categoryId"),
            Map.entry("category", "categoryId"),
            Map.entry("entityCode", "entityCode"),
            Map.entry("department", "department"),
            Map.entry("workflowStage", "workflowStage"),
            Map.entry("milestone", "milestone"),
            Map.entry("rbioOfficeCode", "rbioOfficeCode"),
            Map.entry("groundOfComplaintId", "groundOfComplaintId"),
            Map.entry("compensationType", "compensationType"),
            Map.entry("maintainabilityDetermination", "maintainabilityDetermination"),
            Map.entry("priority", "priority"),
            Map.entry("assignedOfficer", "assignedOfficer"),
            Map.entry("assignedRole", "assignedRole"));

    private final ComplaintSearchService searchService;
    private final ObjectMapper objectMapper;
    private final LanguageDetector languageDetector;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.COMPLAINT_INGESTED, groupId = "cms-search-group")
    public void onComplaintIngested(String message, Acknowledgment ack) {
        handle(message, ack, KafkaTopics.COMPLAINT_INGESTED, event -> {
            Map<String, Object> document = buildDocument(event);
            searchService.upsert(event.getComplaintId(), document);
        });
    }

    @KafkaListener(
            topics = {KafkaTopics.COMPLAINT_ASSIGNED, KafkaTopics.COMPLAINT_IN_PROGRESS,
                    KafkaTopics.COMPLAINT_ESCALATED, KafkaTopics.COMPLAINT_RESOLVED,
                    KafkaTopics.COMPLAINT_CLOSED},
            groupId = "cms-search-group"
    )
    public void onComplaintStatusChange(String message, Acknowledgment ack) {
        handle(message, ack, "complaint.status", event -> {
            Map<String, Object> partial = new HashMap<>();
            if (event.getCurrentStatus() != null) {
                partial.put("status", event.getCurrentStatus().name());
            }
            if (event.getOccurredAt() != null) {
                partial.put("updatedAt", event.getOccurredAt().toString());
            }
            if (event.getAssignedTo() != null) {
                partial.put("assignedOfficer", event.getAssignedTo());
            }

            // upsert, not index: this map holds two or three keys, and the old code passed it to a
            // full IndexRequest, which replaced the document with a stub and destroyed the subject
            // and description that search matches on.
            searchService.upsert(event.getComplaintId(), partial);
        });
    }

    /**
     * Single funnel for both listeners so the acknowledgement and dead-letter rules cannot diverge.
     *
     * The failure rule is the point. Previously a failure logged and returned without acknowledging,
     * so the broker redelivered the same message forever: one malformed payload stalled the whole
     * {@code cms-search-group} and every later complaint silently stopped being indexed. Now a
     * message that cannot be processed is routed to the DLQ and acknowledged, so the partition keeps
     * moving and the poison message is preserved for inspection rather than dropped.
     */
    private void handle(String message, Acknowledgment ack, String source, EventHandler handler) {
        ComplaintEvent event;
        try {
            event = objectMapper.readValue(message, ComplaintEvent.class);
        } catch (Exception e) {
            deadLetter(message, source, "unparseable: " + e.getMessage());
            ack.acknowledge();
            return;
        }

        if (event.getComplaintId() == null || event.getComplaintId().isBlank()) {
            deadLetter(message, source, "event carries no complaintId");
            ack.acknowledge();
            return;
        }

        try {
            handler.accept(event);
            ack.acknowledge();
        } catch (Exception e) {
            // Retry is bounded by the consumer-level error handler configured in KafkaConsumerConfig.
            // Reaching here means those attempts are spent.
            log.error("Indexing failed for complaint {} after retries: {}", event.getComplaintId(), e.getMessage());
            deadLetter(message, source, e.getMessage());
            ack.acknowledge();
        }
    }

    private void deadLetter(String message, String source, String reason) {
        log.error("Dead-lettering message from {}: {}", source, reason);
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("source", source);
            envelope.put("consumer", "cms-search-service");
            envelope.put("reason", reason);
            envelope.put("originalMessage", message);
            kafkaTemplate.send(KafkaTopics.COMPLAINT_DLQ, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("Could not write to DLQ topic {}: {}", KafkaTopics.COMPLAINT_DLQ, e.getMessage());
        }
    }

    private Map<String, Object> buildDocument(ComplaintEvent event) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("complaintId", event.getComplaintId());
        if (event.getCurrentStatus() != null) {
            doc.put("status", event.getCurrentStatus().name());
        }
        if (event.getOccurredAt() != null) {
            doc.put("createdAt", event.getOccurredAt().toString());
        }

        if (event.getPayload() != null && !event.getPayload().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
                for (Map.Entry<String, String> allowed : PAYLOAD_FIELDS.entrySet()) {
                    Object value = payload.get(allowed.getKey());
                    if (value != null) {
                        doc.put(allowed.getValue(), String.valueOf(value));
                    }
                }
            } catch (Exception e) {
                log.warn("Complaint {} payload was not JSON, indexing headers only: {}",
                        event.getComplaintId(), e.getMessage());
            }
        }

        doc.put("detectedLanguage", languageDetector.detect(
                (String) doc.get("subject"), (String) doc.get("description")));

        return doc;
    }

    @FunctionalInterface
    private interface EventHandler {
        void accept(ComplaintEvent event) throws Exception;
    }
}
