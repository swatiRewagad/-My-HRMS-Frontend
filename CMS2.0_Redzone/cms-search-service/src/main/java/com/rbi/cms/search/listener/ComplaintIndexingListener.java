package com.rbi.cms.search.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.search.service.ComplaintSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.opensearch.enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintIndexingListener {

    private final ComplaintSearchService searchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.COMPLAINT_INGESTED, groupId = "cms-search-group")
    public void onComplaintIngested(String message, Acknowledgment ack) {
        try {
            ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
            log.info("Indexing new complaint: {}", event.getComplaintId());

            Map<String, Object> document = buildDocument(event);
            searchService.indexComplaint(event.getComplaintId(), document);

            ack.acknowledge();
            log.info("Indexed complaint: {}", event.getComplaintId());
        } catch (Exception e) {
            log.error("Failed to index complaint from event: {}", message, e);
        }
    }

    @KafkaListener(
            topics = {KafkaTopics.COMPLAINT_ASSIGNED, KafkaTopics.COMPLAINT_IN_PROGRESS,
                    KafkaTopics.COMPLAINT_ESCALATED, KafkaTopics.COMPLAINT_RESOLVED, KafkaTopics.COMPLAINT_CLOSED},
            groupId = "cms-search-group"
    )
    public void onComplaintStatusChange(String message, Acknowledgment ack) {
        try {
            ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
            log.info("Updating index for complaint: {} -> status: {}", event.getComplaintId(), event.getCurrentStatus());

            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("status", event.getCurrentStatus().name());
            partialUpdate.put("updatedAt", event.getOccurredAt().toString());
            if (event.getAssignedTo() != null) {
                partialUpdate.put("assignedTo", event.getAssignedTo());
            }

            searchService.indexComplaint(event.getComplaintId(), partialUpdate);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to update index for event: {}", message, e);
        }
    }

    private Map<String, Object> buildDocument(ComplaintEvent event) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("complaintId", event.getComplaintId());
        doc.put("status", event.getCurrentStatus().name());
        doc.put("createdAt", event.getOccurredAt().toString());

        if (event.getPayload() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
                doc.putAll(payload);
            } catch (Exception e) {
                doc.put("rawPayload", event.getPayload());
            }
        }

        return doc;
    }
}
