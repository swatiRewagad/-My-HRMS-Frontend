package com.rbi.cms.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.enums.OutboxEventStatus;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.ingestion.entity.OutboxEvent;
import com.rbi.cms.ingestion.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!dev-local")
@RequiredArgsConstructor
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishComplaintEvent(String topic, String key, ComplaintEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(key)
                .aggregateType("COMPLAINT")
                .eventType("COMPLAINT_INGESTED")
                .topic(topic)
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .build();

        outboxRepository.save(outbox);
        log.info("Outbox event saved for complaint: {}", key);
    }
}
