package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "OTP_ATTEMPTS", indexes = {
    @Index(name = "idx_otp_mobile", columnList = "mobileNumber"),
    @Index(name = "idx_otp_session", columnList = "sessionId"),
    @Index(name = "idx_otp_expiry", columnList = "expiresAt"),
    @Index(name = "idx_otp_mobile_active", columnList = "mobileNumber,used,expiresAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String mobileNumber;

    @Column(nullable = false, length = 128)
    private String otpHash;

    @Column(nullable = false, length = 10)
    private String channel;

    @Column(length = 200)
    private String email;

    @Column(nullable = false, length = 100)
    private String sessionId;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.expiresAt == null) {
            this.expiresAt = this.createdAt.plusMinutes(5);
        }
    }
}
