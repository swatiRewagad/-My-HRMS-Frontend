package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * UST5: the DPDP Act, 2023 requires the consent manager to be able to demonstrate, per data principal,
 * exactly what notice was accepted and when. The notice text is therefore snapshotted here rather than
 * resolved from TRANSLATIONS at read time — the wording may be revised later, and a consent record that
 * silently reflects the new wording proves nothing about what the citizen actually saw.
 */
@Entity
@Table(name = "CITIZEN_CONSENTS", indexes = {
    @Index(name = "idx_consent_mobile", columnList = "mobileNumber"),
    @Index(name = "idx_consent_granted_at", columnList = "grantedAt"),
    @Index(name = "idx_consent_mobile_purpose_ts", columnList = "mobileNumber, purpose, grantedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CitizenConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String mobileNumber;

    /** Purpose the consent was collected for, e.g. COMPLAINT_REGISTRATION. */
    @Column(nullable = false, length = 50)
    private String purpose;

    /** Notice edition in force when consent was captured, from cms.auth.consent.version. */
    @Column(nullable = false, length = 20)
    private String consentVersion;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 2000)
    private String noticeText;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    @Column(length = 50)
    private String clientIp;

    @Column(length = 500)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        if (this.grantedAt == null) {
            this.grantedAt = LocalDateTime.now();
        }
    }
}
