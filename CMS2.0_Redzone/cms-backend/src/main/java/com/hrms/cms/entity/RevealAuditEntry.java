package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per PII reveal (UST875).
 *
 * Kept separate from AUDIT_LOG because that table requires a complaint number and is scoped to
 * workflow actions, whereas a reveal must also be recordable for a list view or an export where no
 * single complaint applies.
 */
@Entity
@Table(name = "PII_REVEAL_AUDIT", indexes = {
    @Index(name = "idx_reveal_user", columnList = "userId"),
    @Index(name = "idx_reveal_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_reveal_at", columnList = "revealedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RevealAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String userId;

    @Column(length = 200)
    private String displayName;

    @Column(length = 500)
    private String roles;

    @Column(length = 50)
    private String complaintNumber;

    /** Comma-separated field names that were unmasked. */
    @Column(nullable = false, length = 1000)
    private String fieldsRevealed;

    @Column(length = 100)
    private String context;

    @Column(length = 1000)
    private String justification;

    @Column(length = 50)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime revealedAt;

    @PrePersist
    protected void onCreate() {
        if (this.revealedAt == null) {
            this.revealedAt = LocalDateTime.now();
        }
    }
}
