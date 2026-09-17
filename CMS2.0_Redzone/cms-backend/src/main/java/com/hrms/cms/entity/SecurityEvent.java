package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single security-relevant event (denied access, cross-entity attempt, PII reveal).
 *
 * Individual events are stored because a threshold is meaningless without a count: "5 denials in
 * 10 minutes" cannot be evaluated from log lines. {@link SecurityAlert} is the aggregate raised
 * once a threshold trips.
 */
@Entity
@Table(name = "SECURITY_EVENT", indexes = {
    @Index(name = "idx_secevent_subject", columnList = "subject"),
    @Index(name = "idx_secevent_type", columnList = "eventType"),
    @Index(name = "idx_secevent_at", columnList = "occurredAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 200)
    private String userId;

    @Column(length = 50)
    private String complaintNumber;

    @Column(length = 50)
    private String entityCode;

    @Column(length = 500)
    private String requestPath;

    @Column(length = 50)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }
}
