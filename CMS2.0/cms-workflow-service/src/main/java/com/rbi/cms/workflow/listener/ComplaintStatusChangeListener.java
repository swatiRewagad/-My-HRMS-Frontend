package com.rbi.cms.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.workflow.service.ComplaintWorkflowProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@Profile("!dev-local")
@RequiredArgsConstructor
public class ComplaintStatusChangeListener {

    private final ComplaintWorkflowProcessor workflowService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {KafkaTopics.COMPLAINT_RESOLVED, KafkaTopics.COMPLAINT_CLOSED},
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onStatusChange(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("[WORKFLOW-STATUS] Received status change: topic={}, key={}, offset={}",
                record.topic(), record.key(), record.offset());
        try {
            Map<String, String> payload = objectMapper.readValue(record.value(), Map.class);
            String complaintId = payload.get("complaintId");
            String statusStr = payload.getOrDefault("status", "");
            String remarks = payload.getOrDefault("remarks", "");

            if (complaintId == null || complaintId.isBlank()) {
                log.error("[WORKFLOW-STATUS] Missing complaintId in status change event");
                ack.acknowledge();
                return;
            }

            ComplaintStatus targetStatus = resolveStatus(record.topic(), statusStr);
            workflowService.transitionState(complaintId, targetStatus, remarks);
            ack.acknowledge();

            log.info("[WORKFLOW-STATUS] Status transition applied: complaint={}, status={}",
                    complaintId, targetStatus);
        } catch (Exception e) {
            log.error("[WORKFLOW-STATUS] Failed to process status change: {}", e.getMessage(), e);
            throw new RuntimeException("Status change processing failed", e);
        }
    }

    private ComplaintStatus resolveStatus(String topic, String statusStr) {
        if (!statusStr.isBlank()) {
            try {
                return ComplaintStatus.valueOf(statusStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (KafkaTopics.COMPLAINT_RESOLVED.equals(topic)) return ComplaintStatus.RESOLVED;
        if (KafkaTopics.COMPLAINT_CLOSED.equals(topic)) return ComplaintStatus.CLOSED;
        return ComplaintStatus.IN_PROGRESS;
    }
}
