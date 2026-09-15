package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "extraction_rule", indexes = {
        @Index(name = "idx_rule_active_priority", columnList = "is_active, priority ASC"),
        @Index(name = "idx_rule_target_field", columnList = "target_field, is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Column(name = "rule_code", nullable = false, unique = true, length = 100)
    private String ruleCode;

    @Column(length = 500)
    private String description;

    @Column(name = "pattern_type", nullable = false, length = 20)
    private String patternType; // REGEX or KEYWORD_LIST

    @Column(nullable = false, length = 2000)
    private String pattern;

    @Column(name = "target_field", nullable = false, length = 100)
    private String targetField;

    @Column(name = "extract_group")
    @Builder.Default
    private Integer extractGroup = 0;

    @Column(length = 100)
    private String transform; // UPPERCASE, LOWERCASE, TRIM

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "source_scope", nullable = false, length = 20)
    @Builder.Default
    private String sourceScope = "BOTH"; // BODY, SUBJECT, BOTH

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
