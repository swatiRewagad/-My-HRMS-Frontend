package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Record of a retention run (UST890).
 *
 * Existing delete endpoints destroy rows with no trace at all, so there is no way to answer "what
 * happened to this complaint". A deletion log is the only surviving evidence after the data itself
 * is gone, which is why it is written even for dry runs.
 */
@Entity
@Table(name = "DELETION_LOG", indexes = {
    @Index(name = "idx_deletion_category", columnList = "category"),
    @Index(name = "idx_deletion_at", columnList = "executedAt"),
    @Index(name = "idx_deletion_mode", columnList = "dryRun")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeletionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false, length = 100)
    private String targetTable;

    @Column(nullable = false)
    private int retentionDays;

    @Column(nullable = false)
    private LocalDateTime cutoffDate;

    /** How many rows were affected, or would have been in a dry run. */
    @Column(nullable = false)
    private int rowsAffected;

    @Column(nullable = false, length = 20)
    private String action;

    /** True when nothing was actually destroyed. */
    @Column(nullable = false)
    private boolean dryRun;

    @Column(nullable = false, length = 200)
    private String executedBy;

    @Column(nullable = false)
    private LocalDateTime executedAt;

    @Column(length = 2000)
    private String details;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(length = 2000)
    private String errorDetail;

    @PrePersist
    protected void onCreate() {
        if (this.executedAt == null) {
            this.executedAt = LocalDateTime.now();
        }
    }
}
