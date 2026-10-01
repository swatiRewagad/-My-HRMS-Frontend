package com.rbi.cms.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@Profile("!dev-local")
@RequiredArgsConstructor
public class KafkaWorkflowEventPublisher implements WorkflowEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishAssigned(String complaintId, String department, String officer) {
        publish(KafkaTopics.COMPLAINT_ASSIGNED, complaintId, Map.of(
                "complaintId", complaintId,
                "department", department,
                "assignedOfficer", officer,
                "status", "ASSIGNED",
                "timestamp", Instant.now().toString()
        ));
    }

    @Override
    public void publishInProgress(String complaintId, String officer) {
        publish(KafkaTopics.COMPLAINT_IN_PROGRESS, complaintId, Map.of(
                "complaintId", complaintId,
                "assignedOfficer", officer,
                "status", "IN_PROGRESS",
                "timestamp", Instant.now().toString()
        ));
    }

    @Override
    public void publishEscalated(String complaintId, String reason, String escalatedTo) {
        publish(KafkaTopics.COMPLAINT_ESCALATED, complaintId, Map.of(
                "complaintId", complaintId,
                "reason", reason,
                "escalatedTo", escalatedTo,
                "status", "ESCALATED",
                "timestamp", Instant.now().toString()
        ));
    }

    @Override
    public void publishResolved(String complaintId, String resolutionSummary) {
        publish(KafkaTopics.COMPLAINT_RESOLVED, complaintId, Map.of(
                "complaintId", complaintId,
                "resolutionSummary", resolutionSummary != null ? resolutionSummary : "",
                "status", "RESOLVED",
                "timestamp", Instant.now().toString()
        ));
    }

    @Override
    public void publishClosed(String complaintId) {
        publish(KafkaTopics.COMPLAINT_CLOSED, complaintId, Map.of(
                "complaintId", complaintId,
                "status", "CLOSED",
                "timestamp", Instant.now().toString()
        ));
    }

    private void publish(String topic, String key, Map<String, String> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, json);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[WORKFLOW-PUB] Failed to publish to topic={}, key={}: {}",
                            topic, key, ex.getMessage());
                } else {
                    log.info("[WORKFLOW-PUB] Published to topic={}, key={}, offset={}",
                            topic, key, result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("[WORKFLOW-PUB] Serialization failed for topic={}, key={}: {}",
                    topic, key, e.getMessage());
        }
    }
}
