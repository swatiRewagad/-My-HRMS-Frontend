package com.rbi.cms.assignment.domain.entity;

import com.rbi.cms.assignment.domain.enums.DataType;
import com.rbi.cms.assignment.domain.enums.ValueSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ASGN_ATTRIBUTE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "CODE", nullable = false, length = 100)
    private String code;

    @Column(name = "LABEL", nullable = false, length = 200)
    private String label;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "HELP_TEXT", length = 1000)
    private String helpText;

    @Enumerated(EnumType.STRING)
    @Column(name = "DATA_TYPE", nullable = false, length = 20)
    private DataType dataType;

    @Column(name = "SOURCE_PATH", length = 500)
    private String sourcePath;

    @Column(name = "REQUIRED")
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(name = "VALUE_SOURCE", length = 20)
    private ValueSource valueSource;

    @Column(name = "LOOKUP_API_URL", length = 500)
    private String lookupApiUrl;

    @Column(name = "CASE_SENSITIVE")
    private boolean caseSensitive;

    @Column(name = "INDEXABLE", columnDefinition = "boolean default false")
    private Boolean indexable = false;

    @Column(name = "PII_FLAG")
    private boolean piiFlag;

    @Column(name = "DISPLAY_ORDER")
    private Integer displayOrder;

    @Column(name = "ACTIVE")
    private boolean active;

    @Column(name = "ALLOWED_OPERATORS", length = 1000)
    private String allowedOperators;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (active == false && id == null) active = true;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
