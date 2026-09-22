package com.rbi.cms.ingestion.entity;

import com.rbi.cms.common.enums.OutboxEventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "OUTBOX_EVENT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_seq")
    @SequenceGenerator(name = "outbox_seq", sequenceName = "OUTBOX_EVENT_SEQ", allocationSize = 1)
    @Column(name = "EVENT_ID")
    private Long eventId;

    @Column(name = "AGGREGATE_ID", nullable = false, length = 50)
    private String aggregateId;

    @Column(name = "AGGREGATE_TYPE", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "EVENT_TYPE", nullable = false, length = 100)
    private String eventType;

    @Column(name = "TOPIC", nullable = false, length = 100)
    private String topic;

    @Column(name = "PAYLOAD", nullable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "RETRY_COUNT")
    private Integer retryCount;

    @Column(name = "ERROR_MESSAGE", length = 2000)
    private String errorMessage;

    @Column(name = "CORRELATION_ID", length = 100)
    private String correlationId;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "PUBLISHED_AT")
    private Instant publishedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = OutboxEventStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
