package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A proposed SYSTEM_CONFIG change awaiting a second administrator's approval (UST851).
 *
 * The existing TimelineConfigService applies a config edit immediately and merely records who did
 * it in CONFIG_AUDIT_LOG. That is fine for a reversible display preference, but a nudge threshold
 * governs when RBI staff are told an entity has stalled — one administrator quietly raising it to
 * 365 would suppress the escalation signal across the estate with no second pair of eyes. So the
 * change is staged here and applied only on approval.
 *
 * CONFIG_AUDIT_LOG is still written at the point of application, so the existing audit trail
 * remains the single place to read "what actually changed"; this table records the intent and the
 * two-person decision around it.
 */
@Entity
@Table(name = "CONFIG_CHANGE_REQUEST", indexes = {
    @Index(name = "idx_ccr_status", columnList = "status"),
    @Index(name = "idx_ccr_key", columnList = "configKey"),
    @Index(name = "idx_ccr_requested_at", columnList = "requestedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfigChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String configKey;

    /**
     * The value in force when the request was raised. Stored so an approver sees what they are
     * actually changing away from, and so a stale request can be detected if the value moved in the
     * meantime.
     */
    @Column(length = 500)
    private String currentValue;

    @Column(nullable = false, length = 500)
    private String proposedValue;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false, length = 200)
    private String requestedBy;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    /** PENDING | APPROVED | REJECTED | WITHDRAWN */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 200)
    private String decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 500)
    private String decisionReason;

    @PrePersist
    protected void onCreate() {
        this.requestedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PENDING";
        }
    }
}
