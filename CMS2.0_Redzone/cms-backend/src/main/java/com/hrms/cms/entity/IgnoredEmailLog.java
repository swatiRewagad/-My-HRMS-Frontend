package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An inbound email suppressed before draft creation. Persisted so the suppression is auditable:
 * without this row a dropped citizen email would leave no trace anywhere.
 */
@Entity
@Table(name = "IGNORED_EMAIL_LOG", indexes = {
    @Index(name = "idx_ignored_email_sender", columnList = "senderEmail"),
    @Index(name = "idx_ignored_email_rule", columnList = "matchedRuleId"),
    @Index(name = "idx_ignored_email_received", columnList = "receivedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IgnoredEmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String senderEmail;

    @Column(length = 500)
    private String subject;

    @Column(length = 500)
    private String toRecipients;

    @Column(length = 500)
    private String ccRecipients;

    @Column(length = 500)
    private String bccRecipients;

    @Column(length = 200)
    private String messageId;

    private Long matchedRuleId;

    @Column(length = 300)
    private String matchedRulePattern;

    @Column(length = 20)
    private String matchedRuleField;

    @Column(length = 20)
    private String matchedRuleType;

    @Column(length = 500)
    private String matchedRuleReason;

    private LocalDateTime receivedAt;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.receivedAt == null) this.receivedAt = this.createdAt;
    }
}
