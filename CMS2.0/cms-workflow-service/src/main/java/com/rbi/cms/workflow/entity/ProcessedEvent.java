package com.rbi.cms.workflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "WORKFLOW_PROCESSED_EVENTS", indexes = {
        @Index(name = "idx_processed_event_complaint", columnList = "complaintId"),
        @Index(name = "idx_processed_event_correlation", columnList = "correlationId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, unique = true, length = 128)
    private String eventId;

    @Column(name = "complaint_id", nullable = false, length = 64)
    private String complaintId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
