package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * How long one category of data is kept before purging (UST890).
 *
 * A table rather than properties because retention periods are a compliance decision that changes
 * independently of releases, and each change has to be attributable.
 */
@Entity
@Table(name = "RETENTION_POLICY", uniqueConstraints = {
    @UniqueConstraint(name = "uk_retention_category", columnNames = "category")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false, length = 100)
    private String targetTable;

    @Column(nullable = false, length = 60)
    private String timestampColumn;

    @Column(nullable = false)
    private int retentionDays;

    /**
     * Audit-type data is retained independently of the operational records it describes, so a
     * complaint purge cannot erase the evidence of who did what to it.
     */
    @Column(nullable = false)
    private boolean auditCategory;

    /**
     * When true the row is redacted in place instead of deleted, preserving referential integrity
     * where a hard delete would orphan children.
     */
    @Column(nullable = false)
    private boolean redactInsteadOfDelete;

    @Column(length = 1000)
    private String redactColumns;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 500)
    private String description;

    @Column(length = 200)
    private String updatedBy;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
    }
}
