package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "LOGIN_COOLOFFS", indexes = {
    @Index(name = "idx_cooloff_fingerprint", columnList = "fingerprintHash"),
    @Index(name = "idx_cooloff_mobile", columnList = "mobileNumber"),
    @Index(name = "idx_cooloff_ip", columnList = "clientIp"),
    @Index(name = "idx_cooloff_expiry", columnList = "cooloffUntil")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginCooloff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String fingerprintHash;

    @Column(nullable = false, length = 15)
    private String mobileNumber;

    @Column(nullable = false, length = 45)
    private String clientIp;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private int cooloffSeconds;

    @Column(nullable = false)
    private LocalDateTime cooloffUntil;

    @Column(nullable = false)
    private LocalDateTime lastAttemptAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.lastAttemptAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastAttemptAt = LocalDateTime.now();
    }
}
