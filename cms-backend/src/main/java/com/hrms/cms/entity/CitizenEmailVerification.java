package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CITIZEN_EMAIL_VERIFICATIONS", indexes = {
    @Index(name = "idx_email_verify_mobile", columnList = "mobileNumber"),
    @Index(name = "idx_email_verify_email", columnList = "email"),
    @Index(name = "idx_email_verify_token", columnList = "verificationToken")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CitizenEmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String mobileNumber;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 128)
    private String verificationToken;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.expiresAt == null) {
            this.expiresAt = this.createdAt.plusMinutes(30);
        }
    }
}
