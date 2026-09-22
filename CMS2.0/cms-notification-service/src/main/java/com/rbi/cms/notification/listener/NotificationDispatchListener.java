package com.rbi.cms.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.event.NotificationDispatchEvent;
import com.rbi.cms.notification.service.OutboundDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes dispatch requests from {@code notification.requested}.
 *
 * <p>Unlike the complaint-lifecycle listeners this one does <strong>not</strong> catch dispatch failures.
 * Letting the exception propagate is what gives the configured retries and the dead-letter route any effect;
 * catching it here would ack a message whose work never completed.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchListener {

    private final OutboundDispatchService dispatchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.NOTIFICATION_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onDispatchRequested(String message, Acknowledgment ack) throws Exception {
        NotificationDispatchEvent event = parse(message);
        dispatchService.dispatch(event);
        ack.acknowledge();
    }

    /**
     * An unparseable message is hopeless rather than unlucky, so it is reported as non-retryable and goes
     * to the dead-letter topic on the first attempt instead of blocking the partition.
     */
    private NotificationDispatchEvent parse(String message) {
        try {
            return objectMapper.readValue(message, NotificationDispatchEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unreadable dispatch request: " + e.getMessage(), e);
        }
    }
}
