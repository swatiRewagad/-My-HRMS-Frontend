package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ELIGIBILITY_QUESTION_MASTER", indexes = {
    @Index(name = "idx_eqm_scheme", columnList = "schemeVersion"),
    @Index(name = "idx_eqm_entity_type", columnList = "applicableEntityType"),
    @Index(name = "idx_eqm_key", columnList = "questionKey")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EligibilityQuestionMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String schemeVersion; // RBIOS_2021, RBIOS_2026

    /** ALL, RBIO, CEPC, NON_CEPC — which selected-entity department the question applies to. */
    @Column(nullable = false, length = 20)
    private String applicableEntityType;

    @Column(nullable = false)
    private int questionNumber;

    /** Answer key the wizard stores the response under; also the radio input name. */
    @Column(nullable = false, length = 60)
    private String questionKey;

    @Column(nullable = false, length = 20)
    private String questionType; // select, radio

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(length = 120)
    private String translationKey;

    /** Answer value that makes the complaint non-maintainable; null when the question never blocks. */
    @Column(length = 20)
    private String blockOn;

    @Column(columnDefinition = "TEXT")
    private String blockMessage;

    @Column(length = 120)
    private String blockMessageKey;

    /** Scheme clause the block is issued under, e.g. 10(1)(j). */
    @Column(length = 100)
    private String clauseReference;

    private boolean nonMaintainable;

    @Column(columnDefinition = "TEXT")
    private String simplifiedText;

    @Column(length = 120)
    private String simplifiedTextKey;

    /** Rendered inside its parent question rather than as a step of its own. */
    private boolean inlineSubQuestion;

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
