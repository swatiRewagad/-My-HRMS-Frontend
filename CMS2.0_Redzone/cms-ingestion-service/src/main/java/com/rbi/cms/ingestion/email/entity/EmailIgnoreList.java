package com.rbi.cms.ingestion.email.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "EMAIL_IGNORE_LIST")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailIgnoreList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMAIL_PATTERN", nullable = false, unique = true, length = 300)
    private String emailPattern;

    @Column(name = "PATTERN_TYPE", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PatternType patternType = PatternType.EXACT;

    @Column(name = "REASON", length = 500)
    private String reason;

    @Column(name = "ADDED_BY", length = 100)
    private String addedBy;

    @Column(name = "IS_ACTIVE")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public enum PatternType {
        EXACT,
        DOMAIN,
        WILDCARD
    }
}
