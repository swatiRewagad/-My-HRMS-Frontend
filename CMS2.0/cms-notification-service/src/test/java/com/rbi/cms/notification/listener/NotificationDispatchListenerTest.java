package com.rbi.cms.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.enums.DeliveryStatus;
import com.rbi.cms.common.enums.NotificationChannel;
import com.rbi.cms.common.event.NotificationDispatchEvent;
import com.rbi.cms.notification.channel.EmailDispatchHandler;
import com.rbi.cms.notification.repository.OutboundEmailRow;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The behaviour that only exists because of {@code KafkaConfig}: bounded retries, the dead-letter route, and
 * FAILED being recorded exactly once after retries are exhausted.
 *
 * <p>Retries are squeezed to 1 attempt with a short backoff so the test does not sit through production
 * timings.</p>
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.NOTIFICATION_REQUESTED, KafkaTopics.NOTIFICATION_DLQ})
@TestPropertySource(properties = {
        "spring.sql.init.schema-locations=classpath:schema-simulated-emails.sql",
        "spring.datasource.url=jdbc:h2:mem:dispatch-listener;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // MailSenderAutoConfiguration is deliberately left enabled: mode=SEND below, and DispatchStartupCheck
        // refuses to start when real sending is asked for with no JavaMailSender bean. The bean is never
        // used, because EmailDispatchHandler is mocked out.
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=cms-notification-listener-test",
        "cms.notification.mode=SEND",
        "cms.notification.email-enabled=true",
        "cms.kafka.retry.max-attempts=1",
        "cms.kafka.retry.backoff-ms=50",
        "cms.kafka.consumer.concurrency=1"
})
class NotificationDispatchListenerTest {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private EmbeddedKafkaBroker broker;

    /** Replaces the real handler entirely, so the dispatch outcome is controllable. */
    @MockBean private EmailDispatchHandler emailDispatchHandler;

    private Consumer<String, String> dlqConsumer;

    @BeforeEach
    void setUp() {
        when(emailDispatchHandler.channel()).thenReturn(NotificationChannel.EMAIL);
        jdbcClient.sql("DELETE FROM SIMULATED_EMAILS").update();

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlq-reader-" + UUID.randomUUID(), "true", broker);
        dlqConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
                props, new StringDeserializer(), new StringDeserializer());
        dlqConsumer.subscribe(java.util.List.of(KafkaTopics.NOTIFICATION_DLQ));
        dlqConsumer.poll(Duration.ofMillis(200));
    }

    @AfterEach
    void tearDown() {
        if (dlqConsumer != null) {
            dlqConsumer.close();
        }
    }

    private long insertPending() {
        jdbcClient.sql("""
                        INSERT INTO SIMULATED_EMAILS
                            (MESSAGE_ID, FROM_EMAIL, TO_EMAIL, SUBJECT, BODY, DIRECTION, STATUS,
                             DELIVERY_STATUS, COMPLAINT_NUMBER, SENT_AT, UPDATED_AT)
                        VALUES (:messageId, 'officer@rbi.org.in', 'nodal@bank.example.com', 'Subject',
                                '<p>Body</p>', 'OUTBOUND', 'PENDING', 'PENDING', 'CMS-20260101-ABC123',
                                :now, :now)
                        """)
                .param("messageId", "msg-" + UUID.randomUUID())
                .param("now", LocalDateTime.now())
                .update();
        return jdbcClient.sql("SELECT MAX(ID) FROM SIMULATED_EMAILS").query(Long.class).single();
    }

    private String deliveryStatusOf(long id) {
        return jdbcClient.sql("SELECT DELIVERY_STATUS FROM SIMULATED_EMAILS WHERE ID = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private void publish(long recordId) throws Exception {
        NotificationDispatchEvent event = NotificationDispatchEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .channel(NotificationChannel.EMAIL)
                .recordId(recordId)
                .complaintNumber("CMS-20260101-ABC123")
                .occurredAt(Instant.now())
                .build();
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_REQUESTED, "CMS-20260101-ABC123",
                objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
    }

    @Test
    void shouldDispatchAndMarkSent() throws Exception {
        long id = insertPending();
        doNothing().when(emailDispatchHandler).dispatch(any());

        publish(id);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(deliveryStatusOf(id)).isEqualTo(DeliveryStatus.SENT.name()));
        verify(emailDispatchHandler, times(1)).dispatch(any());
    }

    /**
     * Exhausted retries must leave the row FAILED and a record on the dead-letter topic - one transition,
     * written by the recoverer rather than by the listener.
     */
    @Test
    void shouldRecordFailureAndDeadLetterOnceRetriesAreExhausted() throws Exception {
        long id = insertPending();
        doThrow(new IllegalStateException("SMTP refused the connection"))
                .when(emailDispatchHandler).dispatch(any());

        publish(id);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(deliveryStatusOf(id)).isEqualTo(DeliveryStatus.FAILED.name()));

        String lastError = jdbcClient.sql("SELECT LAST_ERROR FROM SIMULATED_EMAILS WHERE ID = :id")
                .param("id", id).query(String.class).single();
        assertThat(lastError).contains("SMTP refused");

        ConsumerRecord<String, String> dead =
                KafkaTestUtils.getSingleRecord(dlqConsumer, KafkaTopics.NOTIFICATION_DLQ, Duration.ofSeconds(20));
        assertThat(dead.value()).contains(String.valueOf(id));
    }

    /** A redelivered request must not send the mail twice. */
    @Test
    void shouldIgnoreARedeliveredRequest() throws Exception {
        long id = insertPending();
        doNothing().when(emailDispatchHandler).dispatch(any());

        publish(id);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(deliveryStatusOf(id)).isEqualTo(DeliveryStatus.SENT.name()));

        publish(id);
        // Nothing observable changes, so allow time for the second message to be consumed and ignored.
        Thread.sleep(2000);

        verify(emailDispatchHandler, times(1)).dispatch(any());
        assertThat(deliveryStatusOf(id)).isEqualTo(DeliveryStatus.SENT.name());
    }

    /** Unreadable input is hopeless rather than unlucky: straight to the DLQ, no dispatch attempted. */
    @Test
    void shouldDeadLetterAnUnreadableMessageWithoutRetrying() throws Exception {
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_REQUESTED, "CMS-20260101-ABC123", "{ not json")
                .get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dead =
                KafkaTestUtils.getSingleRecord(dlqConsumer, KafkaTopics.NOTIFICATION_DLQ, Duration.ofSeconds(20));
        assertThat(dead.value()).isEqualTo("{ not json");
        verify(emailDispatchHandler, never()).dispatch(any());
    }
}
