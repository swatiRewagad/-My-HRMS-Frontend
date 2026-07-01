package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CAPTCHA_SESSIONS", indexes = {
    @Index(name = "idx_captcha_token", columnList = "captchaToken", unique = true),
    @Index(name = "idx_captcha_expiry", columnList = "expiresAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CaptchaSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String captchaToken;

    @Column(nullable = false, length = 128)
    private String answerHash;

    @Column(nullable = false, length = 20)
    private String captchaType;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.expiresAt == null) {
            this.expiresAt = this.createdAt.plusMinutes(5);
        }
    }
}
