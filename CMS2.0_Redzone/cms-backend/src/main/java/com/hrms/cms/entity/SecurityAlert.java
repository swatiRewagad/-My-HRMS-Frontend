package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A raised suspicious-activity alert (UST873).
 *
 * Persisted rather than logged so repeated probing can be counted across requests and reviewed
 * after the fact; a log line cannot be queried by an admin console or acknowledged.
 */
@Entity
@Table(name = "SECURITY_ALERT", indexes = {
    @Index(name = "idx_alert_type", columnList = "alertType"),
    @Index(name = "idx_alert_subject", columnList = "subject"),
    @Index(name = "idx_alert_raised", columnList = "raisedAt"),
    @Index(name = "idx_alert_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String alertType;

    @Column(nullable = false, length = 20)
    private String severity;

    /** Who or what the alert is about — a user id, or an IP when the caller is unidentified. */
    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 50)
    private String subjectType;

    @Column(nullable = false)
    private int eventCount;

    @Column(nullable = false)
    private int threshold;

    @Column(length = 60)
    private String windowLabel;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(length = 50)
    private String ipAddress;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 200)
    private String acknowledgedBy;

    private LocalDateTime acknowledgedAt;

    @Column(length = 1000)
    private String acknowledgementNote;

    @Column(nullable = false)
    private LocalDateTime raisedAt;

    @PrePersist
    protected void onCreate() {
        if (this.raisedAt == null) {
            this.raisedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = "OPEN";
        }
    }
}
