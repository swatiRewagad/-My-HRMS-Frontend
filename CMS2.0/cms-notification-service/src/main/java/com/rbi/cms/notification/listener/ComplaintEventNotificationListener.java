package com.rbi.cms.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintEventNotificationListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.COMPLAINT_INGESTED, groupId = "cms-notification-group")
    public void onComplaintIngested(String message, Acknowledgment ack) {
        try {
            ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
            log.info("Notification: sending acknowledgement for complaint {}", event.getComplaintId());

            notificationService.sendAcknowledgement(
                    extractField(event.getPayload(), "complainantEmail"),
                    extractField(event.getPayload(), "complainantPhone"),
                    event.getComplaintId()
            );

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send ingestion notification: {}", message, e);
        }
    }

    @KafkaListener(topics = KafkaTopics.COMPLAINT_ASSIGNED, groupId = "cms-notification-group")
    public void onComplaintAssigned(String message, Acknowledgment ack) {
        try {
            ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
            log.info("Notification: complaint {} assigned to {}", event.getComplaintId(), event.getAssignedTo());

            notificationService.sendStatusUpdate(
                    extractField(event.getPayload(), "complainantEmail"),
                    extractField(event.getPayload(), "complainantPhone"),
                    event.getComplaintId(),
                    "ASSIGNED"
            );

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send assignment notification: {}", message, e);
        }
    }

    @KafkaListener(topics = KafkaTopics.COMPLAINT_RESOLVED, groupId = "cms-notification-group")
    public void onComplaintResolved(String message, Acknowledgment ack) {
        try {
            ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
            log.info("Notification: complaint {} resolved", event.getComplaintId());

            notificationService.sendStatusUpdate(
                    extractField(event.getPayload(), "complainantEmail"),
                    extractField(event.getPayload(), "complainantPhone"),
                    event.getComplaintId(),
                    "RESOLVED"
            );

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send resolution notification: {}", message, e);
        }
    }

    @KafkaListener(topics = KafkaTopics.COMPLAINT_ESCALATED, groupId = "cms-notification-group")
    public void onComplaintEscalated(String message, Acknowledgment ack) {
        try {
            ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
            log.info("Notification: complaint {} escalated", event.getComplaintId());

            notificationService.sendStatusUpdate(
                    extractField(event.getPayload(), "complainantEmail"),
                    extractField(event.getPayload(), "complainantPhone"),
                    event.getComplaintId(),
                    "ESCALATED - Your complaint has been escalated for priority resolution"
            );

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send escalation notification: {}", message, e);
        }
    }

    private String extractField(String payload, String field) {
        try {
            if (payload == null) return null;
            var node = objectMapper.readTree(payload);
            return node.has(field) ? node.get(field).asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
