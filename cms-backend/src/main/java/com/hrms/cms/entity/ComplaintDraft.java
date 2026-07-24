package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_DRAFTS", indexes = {
    @Index(name = "idx_draft_phone", columnList = "phone")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String draftId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 200)
    private String entityName;

    @Column(columnDefinition = "CLOB")
    private String formDataJson;

    @Column(columnDefinition = "CLOB")
    private String eligibilityAnswersJson;

    @Column
    private Integer currentStep;

    @Column(length = 30)
    private String phase;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
