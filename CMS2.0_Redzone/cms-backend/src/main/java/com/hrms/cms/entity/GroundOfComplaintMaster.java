package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ground-of-complaint vocabulary for the AA parent-complaint search (V36/V34).
 *
 * A master table rather than an enum for the same reason CLOSURE_CLAUSE_MASTER is one: the Scheme's
 * vocabulary must be changeable as data, and the AA search dropdown must be sourced from the database
 * rather than a hardcoded list that drifts from what complaints actually carry.
 *
 * A ground is an OPERATIONAL grouping of subject matter, not a statutory citation — deliberately no
 * clause column. Clause numbers live in CLOSURE_CLAUSE_MASTER and are never invented.
 */
@Entity
@Table(name = "GROUND_OF_COMPLAINT_MASTER",
       uniqueConstraints = @UniqueConstraint(name = "uq_ground_scheme_code",
                                             columnNames = {"scheme_version", "ground_code"}),
       indexes = {
           @Index(name = "idx_ground_active", columnList = "active"),
           @Index(name = "idx_ground_scheme", columnList = "scheme_version")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroundOfComplaintMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ground_code", nullable = false, length = 60)
    private String groundCode;

    /** English text, and the fallback TranslationService serves when a locale has no row. */
    @Column(nullable = false, length = 300)
    private String label;

    @Column(name = "label_key", length = 160)
    private String labelKey;

    @Column(name = "scheme_version", nullable = false, length = 20)
    private String schemeVersion;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
