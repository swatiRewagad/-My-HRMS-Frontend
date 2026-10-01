package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Scheme closure clauses, and whether each one is appealable — the authority for deciding whether an
 * escalation is an APPEAL or a REPRESENTATION.
 *
 * Why this table exists: the clause vocabulary was a hardcoded {@code List<Map>} inside
 * WorkflowController, and the appeal path never consulted it at all. Classification came from a
 * request parameter, so a citizen-facing legal determination was decided by the browser.
 *
 * Appealability is PARTY-DEPENDENT, which is why there are two flags rather than one boolean:
 * a complainant may appeal a closure under 15(1)(a) or 15(1)(b), whereas a regulated entity may
 * appeal only under 15(1)(b). A single {@code appealable} column cannot express that, and the old
 * hardcoded list's single {@code appellable} flag was both wrong and dead.
 *
 * Rows are scheme-version scoped and date-bounded so that a complaint is always judged under the
 * Scheme in force when it was created — reclassifying historical complaints because a new Scheme
 * was seeded would be a legal defect, not a data refresh.
 */
@Entity
@Table(name = "CLOSURE_CLAUSE_MASTER", indexes = {
    @Index(name = "idx_ccm_scheme_clause", columnList = "schemeVersion,clauseCode"),
    @Index(name = "idx_ccm_scheme", columnList = "schemeVersion"),
    @Index(name = "idx_ccm_active", columnList = "active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClosureClauseMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. RBIOS_2021. Never inferred — always the scheme applicable to the complaint. */
    @Column(nullable = false, length = 20)
    private String schemeVersion;

    /** e.g. 15(1)(a), 16(2)(c). Unique per scheme version, enforced by the migration. */
    @Column(nullable = false, length = 40)
    private String clauseCode;

    @Column(nullable = false, length = 300)
    private String label;

    /** Translation key for the label, so citizen-facing text is never an English literal. */
    @Column(length = 160)
    private String labelKey;

    /** RESOLUTION, NON_MAINTAINABLE, CLOSURE, AWARD. */
    @Column(nullable = false, length = 40)
    private String category;

    /** A complainant may appeal a closure issued under this clause. */
    @Column(nullable = false)
    private boolean appealableByComplainant;

    /** A regulated entity (via its PNO) may appeal a closure issued under this clause. */
    @Column(nullable = false)
    private boolean appealableByEntity;

    /**
     * Roles permitted to CLOSE a complaint under this clause, comma-separated; null means any role.
     * Replaces the role if-chain that used to filter the hardcoded clause list.
     */
    @Column(length = 300)
    private String restrictedToRoles;

    /** Inclusive. Null means "since the beginning of this scheme version". */
    private LocalDate effectiveFrom;

    /** Inclusive. Null means still in force. */
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active;

    private LocalDateTime createdAt;

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

    /** True when this clause is appealable by the given party. */
    public boolean isAppealableBy(AppealParty party) {
        return party == AppealParty.ENTITY ? appealableByEntity : appealableByComplainant;
    }

    /** Who is raising the escalation. Appealability differs between the two. */
    public enum AppealParty {
        COMPLAINANT,
        ENTITY
    }
}
