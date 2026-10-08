package com.hrms.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes email and SMS to Kafka for cms-notification-service to send.
 *
 * <p>cms-backend has no SMTP credentials and no SMS gateway; cms-notification-service holds the only
 * {@code JavaMailSender} in the repository. So dispatch is a hand-off, not a send: this adapter
 * returns {@link Outcome#QUEUED} and the caller's delivery row stays PENDING until the consumer
 * settles it by {@code dispatchRef}.
 *
 * <p>The publish is SYNCHRONOUS — {@code get} with a timeout rather than a fire-and-forget
 * {@code whenComplete} callback like {@link com.hrms.cms.event.ComplaintEventPublisher} uses. That
 * callback style cannot signal failure back to the caller, so a broker outage would leave a row
 * marked PENDING that nothing had ever been published for, indistinguishable from one a consumer is
 * about to pick up. Blocking here lets a publish failure throw, which the caller records as FAILED.
 *
 * <p>PII: the body IS published, because the consumer needs it to compose the message. The topic
 * therefore carries complaint narrative and must be access-controlled like the database. Nothing is
 * logged here beyond the recipient and reference — see {@link LoggingOutboundMessageAdapter} for why.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "cms.notification.dispatch.mode", havingValue = "kafka")
public class KafkaOutboundMessageAdapter implements OutboundMessagePort {

    /**
     * Must equal {@code KafkaTopics.NOTIFICATION_DISPATCH} in cms-common, which cms-notification-service
     * consumes by. Duplicated as a literal because cms-backend does not depend on cms-common — the same
     * reason {@link com.hrms.cms.event.ComplaintEventPublisher} restates its topic names.
     */
    private static final String TOPIC_NOTIFICATION_DISPATCH = "notification.dispatch";

    /** Long enough to ride out a leader election, short enough not to stall a scheduled sweep. */
    private static final long PUBLISH_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaOutboundMessageAdapter(KafkaTemplate<String, String> kafkaTemplate,
                                       ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        log.info("Outbound email/SMS dispatch mode = kafka (topic {})", TOPIC_NOTIFICATION_DISPATCH);
    }

    @Override
    public Outcome send(String channel, String recipient, String subject, String body,
                        String relatedReference, String dispatchRef) {
        if (recipient == null || recipient.isBlank()) {
            throw new OutboundDispatchException("No recipient supplied for " + channel + " dispatch");
        }

        // Callers without a delivery row still need a key on the message for log correlation.
        String ref = (dispatchRef == null || dispatchRef.isBlank())
                ? UUID.randomUUID().toString()
                : dispatchRef;

        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("dispatchRef", ref);
            event.put("channel", channel);
            event.put("recipient", recipient);
            event.put("subject", subject);
            event.put("body", body);
            event.put("relatedReference", relatedReference);
            event.put("queuedAt", Instant.now().toString());

            kafkaTemplate.send(TOPIC_NOTIFICATION_DISPATCH, ref,
                            objectMapper.writeValueAsString(event))
                    .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.debug("Queued {} dispatch ref={} recipient={} related={}",
                    channel, ref, recipient, relatedReference);
            return Outcome.QUEUED;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboundDispatchException("Interrupted queueing " + channel + " dispatch", e);
        } catch (Exception e) {
            throw new OutboundDispatchException(
                    "Failed to queue " + channel + " dispatch for " + relatedReference, e);
        }
    }
}
