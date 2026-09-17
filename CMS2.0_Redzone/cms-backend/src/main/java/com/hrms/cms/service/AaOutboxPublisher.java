package com.hrms.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealOutboxEvent;
import com.hrms.cms.repository.AppealOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes appeal lifecycle events to the transactional outbox.
 *
 * The row is written in the SAME transaction as the workflow change, which is the whole point: either
 * both commit or neither does. cms-outbox-publisher then polls OUTBOX_EVENT and publishes to Kafka with
 * retries, so a broker outage delays delivery instead of losing the event.
 *
 * Deliberately NOT modelled on cms-backend's existing ComplaintEventPublisher, which does a direct
 * @Async kafkaTemplate.send outside the transaction and swallows the failure — that can drop an event
 * whose business change committed, and can emit one whose change rolled back.
 *
 * A serialisation failure is swallowed and logged rather than propagated. An appeal that was correctly
 * accepted must not be un-accepted because a downstream analytics consumer could not be told; the log
 * plus the missing row make the gap visible.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaOutboxPublisher {

    private final AppealOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /** Records {@code event} against {@code appeal}. Never throws. */
    public void publish(Appeal appeal, AaWorkflowEvent event, String actor) {
        if (appeal == null || event == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("appealNumber", appeal.getAppealNumber());
            payload.put("originalComplaintNumber", appeal.getOriginalComplaintNumber());
            payload.put("classificationType", appeal.getClassificationType());
            payload.put("status", appeal.getStatus());
            payload.put("workflowStage", appeal.getWorkflowStage());
            payload.put("assignedRole", appeal.getAssignedRole());
            payload.put("assignedOfficer", appeal.getAssignedOfficer());
            payload.put("entityCode", appeal.getEntityCode());
            payload.put("event", event.name());
            payload.put("performedBy", actor);
            payload.put("occurredAt", java.time.LocalDateTime.now().toString());
            // Appellant contact details are deliberately NOT in the payload. This lands on a Kafka topic
            // any downstream consumer can read, and a citizen's email and phone are not needed to react
            // to a lifecycle event.

            outboxRepository.save(AppealOutboxEvent.builder()
                    .aggregateId(appeal.getAppealNumber())
                    .aggregateType(AppealOutboxEvent.AGGREGATE_TYPE_APPEAL)
                    .eventType(event.name())
                    .topic(event.getTopic())
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(AppealOutboxEvent.STATUS_PENDING)
                    .correlationId(appeal.getAppealNumber())
                    .build());
        } catch (Exception e) {
            log.warn("Could not write outbox event {} for appeal {}: {}",
                    event, appeal.getAppealNumber(), e.getMessage());
        }
    }
}
