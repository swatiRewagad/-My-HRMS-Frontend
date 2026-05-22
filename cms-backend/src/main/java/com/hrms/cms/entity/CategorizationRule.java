package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CATEGORIZATION_RULES", indexes = {
    @Index(name = "idx_rule_category", columnList = "CATEGORY_ID"),
    @Index(name = "idx_rule_status", columnList = "status"),
    @Index(name = "idx_rule_priority", columnList = "priority")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CategorizationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String ruleName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String keywords;

    @Column(name = "CATEGORY_ID", nullable = false)
    private Long categoryId;

    @Column(length = 20, nullable = false)
    private String priority;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(length = 50)
    private String source;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer ruleOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "active";
        if (this.priority == null) this.priority = "medium";
        if (this.ruleOrder == null) this.ruleOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
