package com.rbi.cms.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "AUDIT_LOG")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_seq")
    @SequenceGenerator(name = "audit_log_seq", sequenceName = "AUDIT_LOG_SEQ", allocationSize = 1)
    @Column(name = "LOG_ID")
    private Long logId;

    @Column(name = "ENTITY_TYPE", nullable = false, length = 50)
    private String entityType;

    @Column(name = "ENTITY_ID", nullable = false, length = 50)
    private String entityId;

    @Column(name = "ACTION", nullable = false, length = 100)
    private String action;

    @Column(name = "PREVIOUS_VALUE", length = 4000)
    private String previousValue;

    @Column(name = "NEW_VALUE", length = 4000)
    private String newValue;

    @Column(name = "PERFORMED_BY", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "CORRELATION_ID", length = 100)
    private String correlationId;

    @Column(name = "IP_ADDRESS", length = 50)
    private String ipAddress;

    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
