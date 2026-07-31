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
public class ComplaintEscalatedListener {

    private final ComplaintWorkflowProcessor workflowService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.COMPLAINT_ESCALATED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onComplaintEscalated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("[WORKFLOW-ESCALATION] Received escalation event: key={}, offset={}",
                record.key(), record.offset());
        try {
            Map<String, String> payload = objectMapper.readValue(record.value(), Map.class);
            String complaintId = payload.get("complaintId");
            String reason = payload.getOrDefault("reason", "SLA_BREACH");

            if (complaintId == null || complaintId.isBlank()) {
                log.error("[WORKFLOW-ESCALATION] Missing complaintId in escalation event");
                ack.acknowledge();
                return;
            }

            workflowService.escalateComplaint(complaintId, reason);
            ack.acknowledge();

            log.info("[WORKFLOW-ESCALATION] Escalation processed: complaint={}, reason={}",
                    complaintId, reason);
        } catch (Exception e) {
            log.error("[WORKFLOW-ESCALATION] Failed to process escalation: {}", e.getMessage(), e);
            throw new RuntimeException("Escalation processing failed", e);
        }
    }
}
