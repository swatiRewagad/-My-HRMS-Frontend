package com.rbi.cms.outbox.service;

import com.rbi.cms.common.enums.OutboxEventStatus;
import com.rbi.cms.outbox.entity.OutboxEvent;
import com.rbi.cms.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${cms.outbox.max-retries:5}")
    private int maxRetries;

    @Value("${cms.outbox.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${cms.outbox.poll-interval-ms:5000}")
    @SchedulerLock(name = "outboxPublisher", lockAtMostFor = "4m", lockAtLeastFor = "30s")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEvents(OutboxEventStatus.PENDING, maxRetries);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Processing {} pending outbox events", pendingEvents.size());

        int processed = 0;
        for (OutboxEvent event : pendingEvents) {
            if (processed >= batchSize) break;

            try {
                publishEvent(event);
                markAsPublished(event);
                processed++;
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {} (retry {})", event.getEventId(), event.getRetryCount(), e);
                markAsFailed(event, e.getMessage());
            }
        }

        log.info("Published {} outbox events", processed);
    }

    private void publishEvent(OutboxEvent event) {
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload());

        future.join();
    }

    private void markAsPublished(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        outboxRepository.save(event);
    }

    private void markAsFailed(OutboxEvent event, String errorMessage) {
        event.setRetryCount(event.getRetryCount() + 1);
        event.setErrorMessage(errorMessage);

        if (event.getRetryCount() >= maxRetries) {
            event.setStatus(OutboxEventStatus.FAILED);
            log.warn("Outbox event {} exceeded max retries, marked as FAILED", event.getEventId());
        }

        outboxRepository.save(event);
    }
}
