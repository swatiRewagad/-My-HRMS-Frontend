package com.rbi.cms.assignment.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ASGN_AUDIT_EVENT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "ENTITY_TYPE", nullable = false, length = 50)
    private String entityType;

    @Column(name = "ENTITY_ID", nullable = false)
    private Long entityId;

    @Column(name = "ACTION", nullable = false, length = 50)
    private String action;

    @Column(name = "ACTOR", length = 100)
    private String actor;

    @Column(name = "ACTOR_IP", length = 50)
    private String actorIp;

    @Column(name = "CORRELATION_ID", length = 100)
    private String correlationId;

    @Lob
    @Column(name = "BEFORE_JSON")
    private String beforeJson;

    @Lob
    @Column(name = "AFTER_JSON")
    private String afterJson;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
