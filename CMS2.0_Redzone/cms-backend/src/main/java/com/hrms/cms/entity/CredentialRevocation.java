package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Record of a credential revocation (UST877).
 *
 * Also acts as the server-side revocation list. Because this API validates a JWT without calling
 * Keycloak on every request, an access token issued before revocation stays cryptographically valid
 * until it expires; checking incoming tokens against these rows is what makes revocation immediate
 * rather than eventual.
 */
@Entity
@Table(name = "CREDENTIAL_REVOCATION", indexes = {
    @Index(name = "idx_revoke_user", columnList = "username"),
    @Index(name = "idx_revoke_at", columnList = "revokedAt"),
    @Index(name = "idx_revoke_active", columnList = "active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CredentialRevocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String username;

    @Column(length = 100)
    private String keycloakUserId;

    @Column(nullable = false, length = 50)
    private String reason;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, length = 200)
    private String initiatedBy;

    @Column(nullable = false)
    private LocalDateTime revokedAt;

    /** False once access is deliberately restored, so a rejoining employee is not locked out. */
    @Column(nullable = false)
    private boolean active;

    private LocalDateTime restoredAt;

    @Column(length = 200)
    private String restoredBy;

    @Column(nullable = false)
    private boolean accountDisabled;

    @Column(nullable = false)
    private boolean sessionsTerminated;

    private Integer sessionsTerminatedCount;

    @Column(length = 1000)
    private String failureDetail;

    @PrePersist
    protected void onCreate() {
        if (this.revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
        }
    }
}
