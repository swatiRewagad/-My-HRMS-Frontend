package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Which assignment logic an Ombudsman office uses to pick a Dealing Officer (UST468-472).
 *
 * <p>ONE ACTIVE ROW PER OFFICE is the whole point of UST468, and it is enforced by a unique key on
 * {@code officeId} rather than by application code. Two active strategies for one office is not a
 * state the domain has an answer for — whichever row the query happened to return first would decide,
 * so the same complaint could route differently on two pods. A database constraint makes that
 * unrepresentable instead of merely unlikely.
 *
 * <p>The row is the strategy SELECTION, not the mapping data. Entity->officer and category->officer
 * mappings live in their own tables, so switching an office from ENTITY_MAPPING to CATEGORY_MAPPING
 * and back does not destroy either set of mappings.
 *
 * <p><b>Changes are never retrospective.</b> UST468 requires a change to affect only complaints
 * assigned AFTER it. That is achieved by reading this row per assignment and never caching it beyond
 * {@code SystemConfigService}'s short TTL — there is no snapshot taken at startup and no stored
 * decision to migrate. A complaint already assigned keeps its officer because nothing re-runs
 * assignment for it.
 */
@Entity
@Table(name = "OFFICE_ASSIGNMENT_STRATEGY",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_oas_office", columnNames = {"officeId"})
    },
    indexes = {
        @Index(name = "idx_oas_office", columnList = "officeId"),
        @Index(name = "idx_oas_strategy", columnList = "strategy")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfficeAssignmentStrategy {

    /** Rotate among the office's Dealing Officers. The default, and the only one needing no mapping data. */
    public static final String ROUND_ROBIN = "ROUND_ROBIN";

    /** Route by regulated entity: each entity has a named Dealing Officer at this office. */
    public static final String ENTITY_MAPPING = "ENTITY_MAPPING";

    /** Route by complaint category: each category has a named Dealing Officer at this office. */
    public static final String CATEGORY_MAPPING = "CATEGORY_MAPPING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * OFFICE_CODE_MASTER.OFFICE_CODE — e.g. "013" for Mumbai-I.
     *
     * <p>The same office key the complaint carries on {@code rbio_office_code} and the same one
     * OFFICE_THRESHOLD_CONFIG is keyed on, so a complaint, its capacity row and its strategy row all
     * name the office identically. Office NAMES are deliberately not used: they differ between the two
     * office masters ("Mumbai-I" versus "Mumbai I").
     */
    @Column(nullable = false, length = 20)
    private String officeId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String strategy = ROUND_ROBIN;

    /**
     * The role rotated within, or whose members the mappings must name.
     *
     * <p>Configurable rather than hardcoded to RBIO_OFFICER because "Dealing Officer" is spelled three
     * different ways across this codebase (RBIO_DEALING_OFFICIAL, RBIO_OFFICER, AA_DO) and an office
     * may legitimately rotate a different rank.
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String roleGroup = "RBIO_OFFICER";

    @Column(length = 100)
    private String updatedBy;

    @Column(length = 500)
    private String reason;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    /** True when the strategy needs mapping rows to be useful. */
    public boolean requiresMappings() {
        return ENTITY_MAPPING.equals(strategy) || CATEGORY_MAPPING.equals(strategy);
    }

    public static boolean isKnownStrategy(String candidate) {
        return ROUND_ROBIN.equals(candidate)
                || ENTITY_MAPPING.equals(candidate)
                || CATEGORY_MAPPING.equals(candidate);
    }
}
