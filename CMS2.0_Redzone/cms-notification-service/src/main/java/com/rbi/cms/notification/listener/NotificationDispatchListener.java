package com.rbi.cms.notification.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.notification.entity.NotificationDeliveryLog;
import com.rbi.cms.notification.repository.NotificationDeliveryLogRepository;
import com.rbi.cms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Sends the email and SMS that cms-backend queued, then settles the delivery row it wrote.
 *
 * <p>cms-backend records an attempt as PENDING and publishes here because it holds no SMTP credentials
 * and no SMS gateway. The row stays PENDING until this listener resolves it to SENT or FAILED, so a
 * PENDING row older than the poll interval is a real operational signal: the message reached no gateway.
 *
 * <p>ACKNOWLEDGEMENT. The message is acknowledged even when the send fails, because the failure has been
 * recorded on the row and is therefore not lost. Leaving it unacknowledged would redeliver it forever on
 * a permanently bad address and block the partition behind it. A retry is an operator or sweep decision
 * driven off the FAILED row, not an infinite Kafka loop. The one case that does NOT acknowledge is a
 * database failure — there the outcome was never recorded anywhere, so redelivery is the only thing
 * standing between a transient outage and a silently dropped notification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchListener {

    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String CHANNEL_SMS = "SMS";

    private final NotificationService notificationService;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Whether to really contact the gateways. False makes this service a consumer that settles rows
     * without sending, which is what a non-production environment pointed at a real schema needs.
     */
    @Value("${cms.notification.delivery.enabled:true}")
    private boolean deliveryEnabled;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_DISPATCH, groupId = "cms-notification-group")
    @Transactional
    public void onDispatchRequested(String message, Acknowledgment ack) {
        String dispatchRef = null;
        try {
            JsonNode event = objectMapper.readTree(message);
            dispatchRef = text(event, "dispatchRef");
            String channel = text(event, "channel");
            String recipient = text(event, "recipient");
            String subject = text(event, "subject");
            String body = text(event, "body");
            String relatedReference = text(event, "relatedReference");

            if (dispatchRef == null || channel == null || recipient == null) {
                // Unsettleable and unsendable. Acknowledge so it does not block the partition; there is
                // no row to mark FAILED because we do not know which one it was.
                log.error("Malformed dispatch event, discarding: dispatchRef={}, channel={}, recipientPresent={}",
                        dispatchRef, channel, recipient != null);
                ack.acknowledge();
                return;
            }

            String failure = attemptSend(channel, recipient, subject, body, relatedReference, dispatchRef);
            settle(dispatchRef, failure, relatedReference);
            ack.acknowledge();

        } catch (Exception e) {
            // Reaching here means the settle write itself failed, or the payload could not be parsed at
            // all. Either way the outcome is unrecorded, so do NOT acknowledge — redelivery is the only
            // remaining chance to converge.
            log.error("Dispatch handling failed for ref={}, leaving unacknowledged for redelivery: {}",
                    dispatchRef, e.getMessage(), e);
        }
    }

    /** @return null on success, or the failure message to record */
    private String attemptSend(String channel, String recipient, String subject, String body,
                               String relatedReference, String dispatchRef) {
        if (!deliveryEnabled) {
            log.info("DELIVERY DISABLED — {} not sent. ref={}, related={}", channel, dispatchRef, relatedReference);
            return null;
        }

        try {
            if (CHANNEL_EMAIL.equalsIgnoreCase(channel)) {
                notificationService.sendEmail(recipient, subject, body);
            } else if (CHANNEL_SMS.equalsIgnoreCase(channel)) {
                // Still a placeholder inside NotificationService — no SMS vendor is integrated. It
                // returns normally, so this settles SENT. See sendSms.
                notificationService.sendSms(recipient, body);
            } else {
                return "Unsupported channel: " + channel;
            }
            return null;
        } catch (Exception e) {
            log.warn("{} send failed for ref={}: {}", channel, dispatchRef, e.getMessage());
            return truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void settle(String dispatchRef, String failure, String relatedReference) {
        String status = failure == null
                ? NotificationDeliveryLog.STATUS_SENT
                : NotificationDeliveryLog.STATUS_FAILED;

        int updated = deliveryLogRepository.settle(dispatchRef, status, failure, LocalDateTime.now());

        if (updated == 0) {
            // Either a redelivery of an already-settled message, or a dispatch whose caller keeps its
            // own delivery record instead of a NOTIFICATION_DELIVERY_LOG row (COMMUNICATION_OUTBOX,
            // upload links). Neither is an error.
            log.debug("No PENDING row for ref={} (related={}) — already settled or externally tracked",
                    dispatchRef, relatedReference);
        } else {
            log.info("Settled dispatch ref={} as {} (related={})", dispatchRef, status, relatedReference);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** ERROR_MESSAGE is 1000 chars; a stack trace must not fail the update. */
    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
