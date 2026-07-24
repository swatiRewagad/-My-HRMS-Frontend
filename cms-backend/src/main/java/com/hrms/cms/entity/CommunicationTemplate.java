package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMMUNICATION_TEMPLATES", indexes = {
    @Index(name = "idx_template_trigger", columnList = "triggerCondition"),
    @Index(name = "idx_template_scheme", columnList = "schemeVersion"),
    @Index(name = "idx_template_mode", columnList = "mode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommunicationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String templateName;

    @Column(length = 20, nullable = false)
    private String mode; // EMAIL, SMS, LETTER

    @Column(length = 100)
    private String triggerCondition; // CRPC_REJECTION, NEW_COMPLAINT_ACK, CLOSURE, SENT_TO_OTHER_REG, INSUFFICIENT_DETAILS

    @Column(length = 20)
    private String schemeVersion; // RBIOS_2021, RBIOS_2026, BOTH

    @Column(columnDefinition = "TEXT", nullable = false)
    private String subjectTemplate;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String bodyTemplate;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String category; // CLOSURE, ACKNOWLEDGEMENT, NOTIFICATION, REJECTION

    private boolean active;

    @Column(length = 100)
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
