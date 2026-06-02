package com.rbi.cms.eligibility.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "QUESTION_MASTER")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "question_seq")
    @SequenceGenerator(name = "question_seq", sequenceName = "QUESTION_MASTER_SEQ", allocationSize = 1)
    @Column(name = "QUESTION_ID")
    private Long questionId;

    @Column(name = "QUESTION_CODE", nullable = false, unique = true, length = 50)
    private String questionCode;

    @Column(name = "QUESTION_TEXT", nullable = false, length = 1000)
    private String questionText;

    @Column(name = "QUESTION_TYPE", nullable = false, length = 30)
    private String questionType;

    @Column(name = "CATEGORY", length = 50)
    private String category;

    @Column(name = "IS_MANDATORY", nullable = false)
    private Boolean isMandatory;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Column(name = "OPTIONS", length = 2000)
    private String options;

    @Column(name = "EXPECTED_ANSWER", length = 200)
    private String expectedAnswer;

    @Column(name = "RULE_ATTRIBUTE", length = 100)
    private String ruleAttribute;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
