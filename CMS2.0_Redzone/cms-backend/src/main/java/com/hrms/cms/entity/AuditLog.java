package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "AUDIT_LOG", indexes = {
    @Index(name = "idx_audit_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_actor", columnList = "actor"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String complaintNumber;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 200)
    private String actor;

    @Column(length = 50)
    private String actorRole;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 50)
    private String previousState;

    @Column(length = 50)
    private String newState;

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
