package com.rbi.cms.search.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.search.service.ComplaintDocumentNormalizer;
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
        ComplaintEvent event = parseOrDrop(message, ack, true);
        if (event == null) {
            return;
        }

        try {
            searchService.indexComplaint(event.getComplaintId(), buildDocument(event));
            ack.acknowledge();
            log.info("Indexed complaint: {}", event.getComplaintId());
        } catch (Exception e) {
            // Left unacknowledged on purpose: the write may succeed on redelivery.
            log.error("Failed to index complaint {}, leaving offset uncommitted for retry",
                    event.getComplaintId(), e);
        }
    }

    @KafkaListener(
            topics = {KafkaTopics.COMPLAINT_ASSIGNED, KafkaTopics.COMPLAINT_IN_PROGRESS,
                    KafkaTopics.COMPLAINT_ESCALATED, KafkaTopics.COMPLAINT_RESOLVED, KafkaTopics.COMPLAINT_CLOSED},
            groupId = "cms-search-group"
    )
    public void onComplaintStatusChange(String message, Acknowledgment ack) {
        ComplaintEvent event = parseOrDrop(message, ack, true);
        if (event == null) {
            return;
        }

        log.info("Updating index for complaint: {} -> status: {}", event.getComplaintId(), event.getCurrentStatus());

        Map<String, Object> changedFields = new HashMap<>();
        changedFields.put("status", event.getCurrentStatus().name());
        changedFields.put("updatedAt", event.getOccurredAt().toString());
        if (event.getAssignedTo() != null) {
            changedFields.put(ComplaintDocumentNormalizer.FIELD_ASSIGNED_OFFICER, event.getAssignedTo());
        }
        if (event.getDepartment() != null) {
            changedFields.put(ComplaintDocumentNormalizer.FIELD_DEPARTMENT, event.getDepartment());
        }
        if (event.getRegionalOffice() != null) {
            changedFields.put(ComplaintDocumentNormalizer.FIELD_REGIONAL_OFFICE, event.getRegionalOffice());
        }

        try {
            // Must be a partial update: a full index write here would erase every field the event
            // does not carry, including the department the search scope filter depends on.
            searchService.partialUpdate(event.getComplaintId(), changedFields);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to update index for complaint {}, leaving offset uncommitted for retry",
                    event.getComplaintId(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.COMPLAINT_READ, groupId = "cms-search-group")
    public void onComplaintRead(String message, Acknowledgment ack) {
        ComplaintEvent event = parseOrDrop(message, ack, false);
        if (event == null) {
            return;
        }

        // Read state is per officer, so an event that names no reader cannot be applied to anyone and
        // will not become applicable on redelivery.
        if (event.getReadBy() == null || event.getReadBy().isBlank()) {
            log.error("Dropping complaint.read for {} with no readBy", event.getComplaintId());
            ack.acknowledge();
            return;
        }

        try {
            searchService.recordRead(event.getComplaintId(), event.getReadBy());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to mark complaint {} read in the index, leaving offset uncommitted for retry",
                    event.getComplaintId(), e);
        }
    }

    /**
     * Acknowledges and drops messages that can never succeed, so a single poison record does not
     * block the partition on every restart. Returns null when the message was dropped.
     *
     * @param requireStatus whether a missing {@code currentStatus} makes the message undeliverable.
     *                      True for the indexing paths that write the status field; false for
     *                      {@code complaint.read}, which carries no status by design.
     */
    private ComplaintEvent parseOrDrop(String message, Acknowledgment ack, boolean requireStatus) {
        ComplaintEvent event;
        try {
            event = objectMapper.readValue(message, ComplaintEvent.class);
        } catch (Exception e) {
            log.error("Dropping unparseable complaint event: {}", message, e);
            ack.acknowledge();
            return null;
        }

        if (event.getComplaintId() == null || event.getComplaintId().isBlank()) {
            log.error("Dropping complaint event with no complaintId: {}", message);
            ack.acknowledge();
            return null;
        }
        if (requireStatus && event.getCurrentStatus() == null) {
            log.error("Dropping complaint event {} with no currentStatus", event.getComplaintId());
            ack.acknowledge();
            return null;
        }
        return event;
    }

    private Map<String, Object> buildDocument(ComplaintEvent event) {
        Map<String, Object> doc = new HashMap<>();

        if (event.getPayload() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
                doc.putAll(payload);
            } catch (Exception e) {
                doc.put("rawPayload", event.getPayload());
            }
        }

        // Event fields win over the payload copy: they are the authoritative version of this change.
        doc.put(ComplaintDocumentNormalizer.FIELD_COMPLAINT_NUMBER, event.getComplaintId());
        doc.put("status", event.getCurrentStatus().name());
        doc.putIfAbsent("createdAt", event.getOccurredAt().toString());
        if (event.getAssignedTo() != null) {
            doc.put(ComplaintDocumentNormalizer.FIELD_ASSIGNED_OFFICER, event.getAssignedTo());
        }
        if (event.getDepartment() != null) {
            doc.put(ComplaintDocumentNormalizer.FIELD_DEPARTMENT, event.getDepartment());
        }
        if (event.getRegionalOffice() != null) {
            doc.put(ComplaintDocumentNormalizer.FIELD_REGIONAL_OFFICE, event.getRegionalOffice());
        }

        return doc;
    }
}
