package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "AUTO_CLOSURE_QUESTIONS", indexes = {
    @Index(name = "idx_acq_scheme", columnList = "schemeVersion"),
    @Index(name = "idx_acq_entity_type", columnList = "entityType")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AutoClosureQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String schemeVersion; // RBIOS_2021, RBIOS_2026

    @Column(nullable = false, length = 20)
    private String entityType; // RBIO, CEPC, LEGACY

    @Column(nullable = false)
    private int questionNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String helpText;

    @Column(length = 100)
    private String clauseReference;

    @Column(length = 10)
    private String defaultAnswer; // YES or NO

    @Column(length = 50)
    private String outcomeOnYes; // NEXT, CRPC_REJECTION, SUB_JUDICE, NEW_COMPLAINT, SENT_TO_OTHER_REG

    @Column(length = 50)
    private String outcomeOnNo; // NEXT, CRPC_REJECTION, SUB_JUDICE, NEW_COMPLAINT, SENT_TO_OTHER_REG

    @Column(length = 200)
    private String outcomeDetailsYes;

    @Column(length = 200)
    private String outcomeDetailsNo;

    private boolean skipIfPreviousYes;

    private int skipAfterQuestion;

    private boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
