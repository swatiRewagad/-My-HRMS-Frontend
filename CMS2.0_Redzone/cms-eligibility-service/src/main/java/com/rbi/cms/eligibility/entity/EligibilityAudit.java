package com.rbi.cms.eligibility.entity;

import com.rbi.cms.common.enums.EligibilityOutcome;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "ELIGIBILITY_AUDIT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "elig_audit_seq")
    @SequenceGenerator(name = "elig_audit_seq", sequenceName = "ELIGIBILITY_AUDIT_SEQ", allocationSize = 1)
    @Column(name = "AUDIT_ID")
    private Long auditId;

    @Column(name = "SESSION_ID", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "CHANNEL", nullable = false, length = 30)
    private String channel;

    @Column(name = "ANSWERS_JSON", nullable = false, length = 4000)
    private String answersJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "OUTCOME", nullable = false, length = 20)
    private EligibilityOutcome outcome;

    @Column(name = "REASON_CODE", length = 50)
    private String reasonCode;

    @Column(name = "REASON_MESSAGE", length = 500)
    private String reasonMessage;

    @Column(name = "STANDARD_RESPONSE", length = 2000)
    private String standardResponse;

    @Column(name = "IP_ADDRESS", length = 50)
    private String ipAddress;

    @Column(name = "EVALUATED_AT", nullable = false)
    private Instant evaluatedAt;

    @PrePersist
    protected void onCreate() {
        evaluatedAt = Instant.now();
    }
}
