package com.rbi.cms.notification.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.event.NotificationDispatchEvent;
import com.rbi.cms.notification.service.OutboundDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer wiring for dispatch requests: manual acknowledgment, bounded retries, and a dead-letter topic.
 *
 * <p>Applies on <em>every</em> profile, unlike the equivalent in cms-workflow-service which is
 * {@code @Profile("!dev-local")}. Manual ack mode used to be configured only in this module's dev-local yml,
 * which meant that in production the auto-configured container committed offsets by itself while the
 * listener held an {@link org.springframework.kafka.support.Acknowledgment} — so a dispatch that failed was
 * silently marked as consumed.</p>
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // Key names deliberately match cms-workflow-service so the same knob is called the same thing in
    // every service an operator has to tune.
    @Value("${cms.kafka.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${cms.kafka.retry.backoff-ms:2000}")
    private long retryBackoffMs;

    @Value("${cms.kafka.consumer.concurrency:3}")
    private int concurrency;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /** Producer exists only to publish dead letters; this service originates no events of its own. */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            DeadLetterPublishingRecoverer deadLetterRecoverer,
            OutboundDispatchService dispatchService,
            ObjectMapper objectMapper) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // spring.kafka.listener.missing-topics-fatal only configures Boot's auto-configured factory, not a
        // hand-built one like this. Without setting it here the service refuses to start until
        // notification.requested exists, which on a fresh broker it does not.
        factory.getContainerProperties().setMissingTopicsFatal(false);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                failureRecordingRecoverer(deadLetterRecoverer, dispatchService, objectMapper),
                new FixedBackOff(retryBackoffMs, maxRetryAttempts));
        // Neither a malformed message nor an unknown channel can succeed on a retry, so both skip straight
        // to the dead-letter topic rather than occupying the partition for three attempts.
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    /**
     * Marks the row FAILED and then hands the record to the dead-letter publisher, so the database
     * transition and the dead letter happen together and exactly once — after retries are exhausted.
     */
    private org.springframework.kafka.listener.ConsumerRecordRecoverer failureRecordingRecoverer(
            DeadLetterPublishingRecoverer deadLetterRecoverer,
            OutboundDispatchService dispatchService,
            ObjectMapper objectMapper) {

        return (record, exception) -> {
            recordFailure(record, exception, dispatchService, objectMapper);
            deadLetterRecoverer.accept(record, exception);
        };
    }

    private void recordFailure(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                               Exception exception,
                               OutboundDispatchService dispatchService,
                               ObjectMapper objectMapper) {
        try {
            Object value = record.value();
            NotificationDispatchEvent event =
                    objectMapper.readValue(String.valueOf(value), NotificationDispatchEvent.class);
            if (event.getRecordId() != null) {
                dispatchService.recordFailure(event.getRecordId(), rootMessage(exception));
            }
        } catch (Exception e) {
            // An unreadable message has no row to attribute the failure to; the dead letter is the record.
            log.warn("Could not attribute a dispatch failure to a row: {}", e.getMessage());
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(KafkaTopics.NOTIFICATION_DLQ, record.partition()));
        log.info("[KAFKA-CONFIG] Dispatch DLQ configured -> topic: {}", KafkaTopics.NOTIFICATION_DLQ);
        return recoverer;
    }
}
