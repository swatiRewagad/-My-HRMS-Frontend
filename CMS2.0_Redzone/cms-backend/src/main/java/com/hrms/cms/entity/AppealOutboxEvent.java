package com.hrms.cms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A durable record of a domain event, to be published to Kafka by the outbox publisher.
 *
 * Maps the EXISTING OUTBOX_EVENT table (singular) that cms-ingestion-service and cms-outbox-publisher
 * already share, so appeal events drain through the same infrastructure as complaint events and no
 * second publisher is needed.
 *
 * Why cms-backend needs its own mapping at all: it had none. The only Kafka path here was a direct
 * @Async kafkaTemplate.send outside the transaction that swallowed failures — so an event could be lost
 * with the business change committed, or sent for a change that then rolled back. Writing a row in the
 * same transaction as the change makes the two atomic.
 *
 * Named AppealOutboxEvent rather than OutboxEvent because two other modules already define a class by
 * that name against this table; a third identically-named entity in a shared codebase invites the wrong
 * import.
 */
@Entity
@Table(name = "OUTBOX_EVENT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppealOutboxEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String AGGREGATE_TYPE_APPEAL = "APPEAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EVENT_ID")
    private Long eventId;

    /** The appeal number. Also the Kafka message key, so events for one appeal keep their order. */
    @Column(name = "AGGREGATE_ID", nullable = false, length = 50)
    private String aggregateId;

    @Column(name = "AGGREGATE_TYPE", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "EVENT_TYPE", nullable = false, length = 100)
    private String eventType;

    @Column(name = "TOPIC", nullable = false, length = 100)
    private String topic;

    @Column(name = "PAYLOAD", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "RETRY_COUNT")
    private Integer retryCount;

    @Column(name = "ERROR_MESSAGE", length = 2000)
    private String errorMessage;

    @Column(name = "CORRELATION_ID", length = 100)
    private String correlationId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    @PrePersist
    void stamp() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = STATUS_PENDING;
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
    }
}
