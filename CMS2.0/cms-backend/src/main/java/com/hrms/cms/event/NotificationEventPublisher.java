package com.hrms.cms.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.SimulatedEmail;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.enums.NotificationChannel;
import com.rbi.cms.common.event.NotificationDispatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Asks cms-notification-service to dispatch an outbound notification.
 *
 * <p>Separate from {@link ComplaintEventPublisher} because every method there takes a
 * {@link com.hrms.cms.entity.Complaint} and builds a complaint-shaped envelope; the two would share only
 * {@link #send}.</p>
 *
 * <p>Publishing is fire-and-forget, matching {@code ComplaintEventPublisher}: a failure is logged and the
 * caller is not told. The consequence is deliberate but worth stating - if the broker is unreachable the
 * row stays PENDING indefinitely, and the recovery path is the retry endpoint, not an automatic resend.
 * Operationally, {@code delivery_status = 'PENDING'} with a stale {@code updated_at} is what "stuck"
 * looks like.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** Must be called after the row is saved: the event carries only its id, so the id has to exist. */
    @Async("taskExecutor")
    public void publishEmailDispatchRequested(SimulatedEmail email) {
        if (email == null || email.getId() == null) {
            log.error("Refusing to publish a dispatch request without a persisted email id");
            return;
        }

        try {
            NotificationDispatchEvent event = NotificationDispatchEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .channel(NotificationChannel.EMAIL)
                    .recordId(email.getId())
                    .complaintNumber(email.getComplaintNumber())
                    .requestedBy(email.getCreatedBy())
                    .occurredAt(Instant.now())
                    .correlationId(UUID.randomUUID().toString())
                    .build();

            send(KafkaTopics.NOTIFICATION_REQUESTED, email.getComplaintNumber(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to build dispatch request for email {}: {}", email.getId(), e.getMessage());
        }
    }

    private void send(String topic, String complaintNumber, String message) {
        kafkaTemplate.send(topic, complaintNumber, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for {}: {}", topic, complaintNumber, ex.getMessage());
                    } else {
                        log.info("Published {} for {} to partition {}",
                                topic, complaintNumber, result.getRecordMetadata().partition());
                    }
                });
    }
}
