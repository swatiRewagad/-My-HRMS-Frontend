package com.rbi.cms.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.workflow.entity.ProcessedEvent;
import com.rbi.cms.workflow.repository.ProcessedEventRepository;
import com.rbi.cms.workflow.service.ComplaintWorkflowProcessor;
import com.rbi.cms.workflow.service.WorkflowEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@Profile("!dev-local")
@RequiredArgsConstructor
public class ComplaintIngestedListener {

    private final ComplaintWorkflowProcessor workflowService;
    private final WorkflowEventPublisher eventPublisher;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter eventsReceived;
    private Counter eventsProcessed;
    private Counter eventsDuplicate;
    private Counter eventsFailed;
    private Timer processingTimer;

    @PostConstruct
    void initMetrics() {
        eventsReceived = Counter.builder("workflow.events.received")
                .tag("topic", KafkaTopics.COMPLAINT_INGESTED)
                .register(meterRegistry);
        eventsProcessed = Counter.builder("workflow.events.processed")
                .tag("topic", KafkaTopics.COMPLAINT_INGESTED)
                .register(meterRegistry);
        eventsDuplicate = Counter.builder("workflow.events.duplicate")
                .tag("topic", KafkaTopics.COMPLAINT_INGESTED)
                .register(meterRegistry);
        eventsFailed = Counter.builder("workflow.events.failed")
                .tag("topic", KafkaTopics.COMPLAINT_INGESTED)
                .register(meterRegistry);
        processingTimer = Timer.builder("workflow.events.processing.duration")
                .tag("topic", KafkaTopics.COMPLAINT_INGESTED)
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = KafkaTopics.COMPLAINT_INGESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onComplaintIngested(ConsumerRecord<String, String> record, Acknowledgment ack) {
        eventsReceived.increment();
        String eventId = buildEventId(record);

        log.info("[WORKFLOW-LISTENER] Received event: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            if (processedEventRepository.existsByEventId(eventId)) {
                log.warn("[WORKFLOW-LISTENER] Duplicate event skipped: {}", eventId);
                eventsDuplicate.increment();
                ack.acknowledge();
                return;
            }

            ComplaintEvent event = objectMapper.readValue(record.value(), ComplaintEvent.class);
            validateEvent(event);

            String processInstanceId = workflowService.startComplaintWorkflow(event);

            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .complaintId(event.getComplaintId())
                    .correlationId(event.getCorrelationId())
                    .processInstanceId(processInstanceId)
                    .processedAt(Instant.now())
                    .build());

            eventPublisher.publishAssigned(event.getComplaintId(), "WORKFLOW", processInstanceId);

            ack.acknowledge();
            eventsProcessed.increment();

            log.info("[WORKFLOW-LISTENER] Workflow started: complaint={}, processId={}, eventId={}",
                    event.getComplaintId(), processInstanceId, eventId);

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[WORKFLOW-LISTENER] Invalid JSON payload - sending to DLQ: offset={}, error={}",
                    record.offset(), e.getMessage());
            eventsFailed.increment();
            throw new IllegalArgumentException("Invalid event payload", e);
        } catch (IllegalArgumentException e) {
            log.error("[WORKFLOW-LISTENER] Validation failed - sending to DLQ: {}", e.getMessage());
            eventsFailed.increment();
            throw e;
        } catch (Exception e) {
            log.error("[WORKFLOW-LISTENER] Processing failed (will retry): offset={}, error={}",
                    record.offset(), e.getMessage(), e);
            eventsFailed.increment();
            throw new RuntimeException("Event processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void validateEvent(ComplaintEvent event) {
        if (event.getComplaintId() == null || event.getComplaintId().isBlank()) {
            throw new IllegalArgumentException("complaintId is required");
        }
        if (event.getCorrelationId() == null || event.getCorrelationId().isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
    }

    private String buildEventId(ConsumerRecord<String, String> record) {
        return String.format("%s-%d-%d", record.topic(), record.partition(), record.offset());
    }
}
