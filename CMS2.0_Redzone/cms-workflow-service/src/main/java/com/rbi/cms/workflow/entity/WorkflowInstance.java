package com.rbi.cms.workflow.entity;

import com.rbi.cms.common.enums.ComplaintStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "WORKFLOW_INSTANCES", indexes = {
        @Index(name = "idx_wf_complaint_id", columnList = "complaintId", unique = true),
        @Index(name = "idx_wf_process_id", columnList = "processInstanceId"),
        @Index(name = "idx_wf_status", columnList = "status"),
        @Index(name = "idx_wf_department", columnList = "department"),
        @Index(name = "idx_wf_assigned_officer", columnList = "assignedOfficer")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false, unique = true, length = 64)
    private String complaintId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    @Column(name = "department", length = 32)
    private String department;

    @Column(name = "assigned_officer", length = 64)
    private String assignedOfficer;

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "priority", length = 16)
    private String priority;

    @Column(name = "current_task", length = 128)
    private String currentTask;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ComplaintStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "escalation_reason", length = 512)
    private String escalationReason;

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
