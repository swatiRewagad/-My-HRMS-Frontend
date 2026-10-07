package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * How often one closure clause was cited by one cohort, precomputed (Brief 21 §5.3.2).
 *
 * <h2>What a row MEANS, said precisely</h2>
 * "Of the {@link #cohortTotal} complaints that closed with a clause recorded in {@link #department},
 * under category {@link #categoryKey} and against entity {@link #entityKey},
 * {@link #occurrences} of them cited {@link #clauseCode}." Nothing more. It is a COUNT of what the
 * register already did, which keeps this inside the brief's Tier 1 — no inference over case text, no
 * model artifact, and no judgement about whether the clause was the right one.
 *
 * <h2>This is NOT the entity-clause-precedent signal that already ships</h2>
 * That one answers a single count beside the complaint ("23 earlier complaints against this entity
 * cited 16(2)(a)") and reorders nothing. This table is a DISTRIBUTION, which is what a ranking needs.
 *
 * <h2>Why there is one row per (cohort, CLAUSE) and not one per cohort</h2>
 * {@link AssistanceNextAction} stores only its winner, because the rail shows one suggestion. The
 * deliverable here is a REORDERING of a {@code <select>} carrying fifteen options, and a single winner
 * could annotate one of them while leaving fourteen in alphabetical order — which is not a ranking. So
 * the surviving distribution is stored, capped per cohort by the refresh service, and the read is still
 * one index range rather than a sort.
 *
 * <h2>The key is NOT the four columns the brief names, and that is measured</h2>
 * §5.3.2 asks for (category, ground, entity type, resolution path). Against {@code cms_db} on
 * 2026-10-07:
 * <ul>
 *   <li><b>ground is impossible.</b> {@code COMPLAINTS.ground_of_complaint_id} is populated on 0 of
 *       4403 rows. Not few — zero. A key part that is NULL everywhere yields no cohort, no precision
 *       and no fallback, so it is not a key column here. This is the brief being wrong about the code.
 *   <li><b>category is nearly empty</b>, so it is a key part carrying {@link #CATEGORY_AGNOSTIC}.
 *       877 rows carry a clause, 870 of those are closed, and only 64 of those 870 also carry a
 *       category_id — all 64 in a single category.
 *   <li><b>entity type is populated yet signal-free today</b>, and is kept with {@link #ENTITY_AGNOSTIC}
 *       so the precision arrives with the data rather than with a migration. {@code entity_code} is
 *       non-null on all 877 clause-bearing rows but holds two distinct values among them (876 and 1),
 *       so the entity-specific cohorts are numerically identical to the agnostic ones right now.
 *   <li><b>resolution path does not exist as a column</b>, and its nearest neighbour
 *       ({@code closure_cause}) is disqualified by LEAKAGE, not by sparsity:
 *       {@code CepcWorkflowService:410/419} and {@code RbioWorkflowService:665/704} write it in the
 *       same call as the clause it would predict, and only 23 of 1422 not-yet-closed complaints carry
 *       one. {@link #department} is used instead — it separates the CEPC/RBIO/CRPC machines and their
 *       clause vocabularies, and it is known BEFORE closure on 1402 of 1422 open complaints.
 * </ul>
 *
 * <h2>Both sentinels are NOT NULL, which is not the natural spelling</h2>
 * A composite UNIQUE key containing a NULL prevents no duplicate rows on MySQL or on Oracle, so a NULL
 * key part would make this table non-idempotent and every refresh would append a fresh set of agnostic
 * rows instead of updating the existing ones. {@link #ENTITY_AGNOSTIC} is {@code "*"} rather than
 * {@code ""} for a second reason on top of that: Oracle treats the empty string AS NULL, so an
 * empty-string sentinel would silently be the NULL it was written to avoid.
 *
 * <h2>Advisory, and the read side must keep it that way</h2>
 * Mined from what HAPPENED, not from what is permitted. The clause a cohort cited most often may be one
 * this caller's role may not cite at all — {@code 15(1)(a)}, which dominates every cohort in the
 * current register, is restricted to {@code OMBUDSMAN,RBIO_ADMIN,ADMIN}. So the read path MUST filter
 * through {@code ClosureClauseAccessService} (§5.3.2 and §4 both require it) and must never auto-select:
 * the {@code <select>} this annotates commits a real closure.
 *
 * @see com.hrms.cms.service.ClauseRecommendationRefreshService the only writer
 */
@Entity
@Table(name = "ASSISTANCE_CLAUSE_PRIOR",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_ACP_COHORT_CLAUSE",
                columnNames = {"DEPARTMENT", "CATEGORY_KEY", "ENTITY_KEY", "CLAUSE_CODE"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistanceClausePrior {

    /** {@code CATEGORY_KEY} for a cohort that is not category-specific. See the class javadoc. */
    public static final long CATEGORY_AGNOSTIC = 0L;

    /**
     * {@code ENTITY_KEY} for a cohort that is not entity-specific.
     *
     * <p>{@code "*"} and not {@code ""}: Oracle treats an empty string as NULL, which would defeat the
     * unique constraint the sentinel exists to keep meaningful, and {@code ""} is in any case already
     * used by some source rows to mean "no entity recorded" — a different statement from "all
     * entities".
     */
    public static final String ENTITY_AGNOSTIC = "*";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The routing department, standing in for the brief's "resolution path". Width matched to
     * {@code COMPLAINTS.department} so a value cannot be silently truncated into the rollup.
     *
     * <p>Stored VERBATIM, not case-folded, matching {@link AssistanceNextAction#getFromStatus}'s
     * reasoning: the measured consequence is that MySQL's {@code ai_ci} collation folds the read
     * comparison for free while Oracle does not, so the lookup is case-insensitive on one engine and
     * sensitive on the other. The source values are a closed set written by the workflow services
     * ({@code CEPC}, {@code RBIO}, {@code CRPC}) and were verified consistently upper-cased, so there
     * is nothing to fold today.
     */
    @Column(name = "DEPARTMENT", nullable = false, length = 20)
    private String department;

    /** A real {@code COMPLAINTS.category_id}, or {@link #CATEGORY_AGNOSTIC}. Never null. */
    @Column(name = "CATEGORY_KEY", nullable = false)
    @Builder.Default
    private Long categoryKey = CATEGORY_AGNOSTIC;

    /**
     * {@code UPPER(TRIM(entity_code))}, or {@link #ENTITY_AGNOSTIC}. Never null.
     *
     * <p>NORMALISED ON WRITE, which is the §6.2-compliant half of a problem it does not solve.
     * {@code entity_code} is documented dirty — the same entity appears as both
     * {@code 'Punjab National Bank'} and {@code 'PNB'} — and fixing that globally is a data-migration
     * project owned elsewhere. Upper-casing and trimming here merges case and whitespace variants, and
     * lets the READ seek this column without a function wrapper that would defeat the index. Two
     * genuinely different spellings of one entity remain two cohorts; that is the dirt surviving, and
     * it is reported rather than silently corrected.
     */
    @Column(name = "ENTITY_KEY", nullable = false, length = 50)
    @Builder.Default
    private String entityKey = ENTITY_AGNOSTIC;

    /**
     * The clause, as the RAW {@code COMPLAINTS.closure_clause} value ({@code 15(1)(a)}).
     *
     * <p>A machine key rather than prose, because the client matches it against the {@code <option>}
     * values the closure picker already renders — those come from {@code CLOSURE_CLAUSE_MASTER}'s
     * {@code clause_code}, so this must be the same vocabulary. The displayed label comes from the
     * master table and the sentence around it from the i18n bundle; nothing here is shown directly.
     */
    @Column(name = "CLAUSE_CODE", nullable = false, length = 100)
    private String clauseCode;

    /** How many complaints in this cohort cited {@link #clauseCode}. The numerator. */
    @Column(name = "OCCURRENCES", nullable = false)
    private Long occurrences;

    /**
     * How many complaints in this cohort closed with ANY clause. The denominator.
     *
     * <p>{@code nullable = false} beside the numerator, both always, because Brief 21 is explicit that
     * "a bare recommendation with no denominator will be distrusted, correctly". A schema permitting
     * the numerator alone would permit the picker to say "usually 15(1)(a)" with nothing behind it, and
     * an officer has no way to tell that from a figure drawn from 800 closures.
     */
    @Column(name = "COHORT_TOTAL", nullable = false)
    private Long cohortTotal;

    /**
     * When the refresh that wrote this row ran.
     *
     * <p>Read by nothing in the request path. It exists so that a rollup which quietly stopped
     * refreshing is diagnosable — the one failure a scheduled job has that an endpoint does not, since
     * stale counts look exactly like correct ones — and it is the stale sweep's only predicate.
     */
    @Column(name = "REFRESHED_AT", nullable = false)
    private LocalDateTime refreshedAt;

    /** This clause's share of its cohort, 0..1. Derived, never stored — the two counts are the truth. */
    @Transient
    public double share() {
        if (cohortTotal == null || cohortTotal <= 0 || occurrences == null) {
            return 0d;
        }
        return (double) occurrences / (double) cohortTotal;
    }

    /** True when this row is the category-agnostic fallback rather than a category-specific cohort. */
    @Transient
    public boolean isCategoryAgnostic() {
        return categoryKey == null || categoryKey == CATEGORY_AGNOSTIC;
    }

    /** True when this row is the entity-agnostic fallback rather than an entity-specific cohort. */
    @Transient
    public boolean isEntityAgnostic() {
        return entityKey == null || ENTITY_AGNOSTIC.equals(entityKey);
    }

    /**
     * How SPECIFIC this row's cohort is: 2 both, 1 one sentinel, 0 fully agnostic.
     *
     * <p>Used by the read to prefer a narrower cohort over a broader one. A derived integer rather than
     * an {@code ORDER BY} because the preference is a RULE the reader should be able to see and a unit
     * test should be able to pin — and because sorting in SQL reintroduced {@code Using filesort} on a
     * query that is otherwise a clean index range.
     */
    @Transient
    public int specificity() {
        return (isCategoryAgnostic() ? 0 : 1) + (isEntityAgnostic() ? 0 : 1);
    }
}
