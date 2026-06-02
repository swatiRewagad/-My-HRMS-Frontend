package com.rbi.cms.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.workflow.service.ComplaintWorkflowProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintIngestedListener {

    private final ComplaintWorkflowProcessor workflowService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.COMPLAINT_INGESTED, groupId = "cms-workflow-group")
    public void onComplaintIngested(String message, Acknowledgment ack) {
        try {
            ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
            log.info("Received complaint.ingested event for: {}", event.getComplaintId());

            workflowService.startComplaintWorkflow(event);

            ack.acknowledge();
            log.info("Successfully processed complaint.ingested for: {}", event.getComplaintId());
        } catch (Exception e) {
            log.error("Failed to process complaint.ingested event: {}", message, e);
            throw new RuntimeException("Event processing failed", e);
        }
    }
}
