package com.rbi.cms.rules.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "RULE_VERSION_HISTORY")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleVersionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private RuleDefinition rule;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "drl_content", nullable = false, columnDefinition = "CLOB")
    private String drlContent;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", updatable = false)
    private Instant changedAt;

    @Column(name = "action", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RuleAction action;

    @PrePersist
    public void prePersist() {
        if (changedAt == null) changedAt = Instant.now();
    }
}
