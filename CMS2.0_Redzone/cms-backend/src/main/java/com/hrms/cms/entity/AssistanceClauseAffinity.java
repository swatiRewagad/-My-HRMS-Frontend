package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * How often ONE closure clause was cited by ONE cohort of past closures (Brief 21 §5.3.2).
 *
 * <h2>What a row MEANS, said precisely</h2>
 * "Of the {@link #cohortTotal} complaints closed under this (scheme, department, category, ground,
 * entity type, resolution path), {@link #occurrences} of them cited {@link #clauseCode}." Nothing more.
 * It is a COUNT over {@code COMPLAINTS.closure_clause} — no inference over case text, no model
 * artifact, and no judgement about whether the clause was correctly cited. That is what keeps §5.3.2
 * inside Tier 1.
 *
 * <h2>This is NOT the shipped {@code entity-clause-precedent} prior, and the resemblance is a trap</h2>
 * That prior reports a count BESIDE the complaint in the rail — "this clause has been cited N times for
 * this entity" — and reorders nothing. §5.3.2 is a different surface: it RANKS the closure-clause
 * {@code <select>}'s own options. A reader comparing the two by name would conclude §5.3.2 shipped.
 * Two independent reads over the same column answering two different questions; neither replaces the
 * other.
 *
 * <h2>ONE ROW PER CLAUSE, unlike {@link AssistanceNextAction}</h2>
 * The next-action rollup stores only the winning action, because the rail shows one suggestion. This
 * one cannot: the deliverable is an ORDERING of every clause the role may cite, so the read needs the
 * whole distribution for its cohort, not its argmax. That is still "a single keyed lookup" per §5.3 —
 * one index range on the leading cohort columns returning at most {@link #MAX_CLAUSES_PER_COHORT} rows
 * — rather than a request-time {@code GROUP BY}.
 *
 * <h2>The SENTINEL, and why there are four of them</h2>
 * The brief's key is (category, ground, entity type, resolution path). MEASURED against {@code cms_db},
 * three of those four are empty or near-empty on the rows that carry a closure clause:
 * <ul>
 *   <li>{@code ground_of_complaint_id} is populated on <b>0 of 4,403</b> complaints. Not sparse —
 *       absent. The column exists and nothing writes it.
 *   <li>{@code category_id} is on <b>65 of 877</b> clause-bearing rows (7%).
 *   <li>{@code entity_type} is reachable on <b>1 of 877</b>: {@code entity_code} holds
 *       {@code 'Test Bank Ltd'} on 876 of them, which matches no {@code REGULATED_ENTITIES.name}, so
 *       the join yields nothing. This is the documented {@code entity_code} dirt, handled where it is
 *       read rather than globally cleaned.
 *   <li>The brief's "resolution path" has no column. {@code closure_cause} is the closest and is set
 *       AT closure — 10 of 1,200 open complaints have one — so it is not knowable when the officer is
 *       picking a clause, which makes it useless as a request-time key.
 *       {@code maintainability_determination} is used instead; see {@link #resolutionPath}.
 * </ul>
 * So every dimension carries a {@code '*'} (or {@code 0}) sentinel meaning "this cohort is not specific
 * on this dimension", and the refresh writes a row for every combination of specific-or-sentinel that
 * clears the floors. The read walks from most specific to least and takes the first cohort that
 * answers. Precision where the data supports it, coverage everywhere else — and the fallback is a
 * stored row rather than a second query shape.
 *
 * <p>SENTINELS, not NULLs, for the same reason {@link AssistanceNextAction} gives: a composite UNIQUE
 * key containing a NULL prevents no duplicates on either MySQL or Oracle, so a NULL dimension would
 * make the key non-idempotent and every refresh would append a fresh set of rows instead of updating.
 *
 * <h2>No PII, structurally</h2>
 * Six key columns, a clause code, two counts and a timestamp. There is no column here that could hold
 * a complainant name, phone, email, address or account number, so §4's PII rule is enforced by the
 * schema and not only by the serialiser.
 *
 * @see com.hrms.cms.service.AssistanceClauseAffinityRefreshService the only writer
 * @see com.hrms.cms.service.ClosureClauseRecommendationService the only reader
 */
@Entity
@Table(name = "ASSISTANCE_CLAUSE_AFFINITY",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_ACA_COHORT",
                columnNames = {"SCHEME_VERSION", "DEPARTMENT", "CATEGORY_KEY", "GROUND_KEY",
                        "ENTITY_TYPE", "RESOLUTION_PATH", "CLAUSE_CODE"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistanceClauseAffinity {

    /** {@code CATEGORY_KEY} / {@code GROUND_KEY} for a cohort that is not specific on that dimension. */
    public static final long NUMERIC_ANY = 0L;

    /** {@code DEPARTMENT} / {@code ENTITY_TYPE} / {@code RESOLUTION_PATH} wildcard. */
    public static final String TEXT_ANY = "*";

    /**
     * Row cap for one cohort's read.
     *
     * <p>{@code CLOSURE_CLAUSE_MASTER} holds 15 rows today, all under {@code RBIOS_2021}, so a cohort
     * can hold at most 15 clauses — this is 64 to leave room for a 2026 set without the cap becoming
     * the thing that silently truncates an ordering. §6.2 requires a DECLARED cap on every query, and
     * a cap chosen from the vocabulary's size rather than from a round number is the one that can be
     * reasoned about when the vocabulary grows.
     */
    public static final int MAX_CLAUSES_PER_COHORT = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The scheme the closures in this cohort were decided under, e.g. {@code RBIOS_2021}.
     *
     * <p>Part of the key and never a sentinel, because a clause citation under one Scheme is not
     * evidence about a different Scheme's clause vocabulary — {@code CLOSURE_CLAUSE_MASTER} is itself
     * scheme-scoped and date-bounded so that "a complaint is judged under the Scheme in force when it
     * was created". Pooling schemes would be the one dimension where a wildcard is a correctness bug
     * rather than a loss of precision.
     *
     * <p>Stored UPPER-CASED by the refresh. See {@link #resolutionPath} for why normalisation on write
     * is the engine-portable choice.
     */
    @Column(name = "SCHEME_VERSION", nullable = false, length = 20)
    private String schemeVersion;

    /** {@code CEPC} / {@code RBIO}, or {@link #TEXT_ANY}. Upper-cased by the refresh. */
    @Column(name = "DEPARTMENT", nullable = false, length = 20)
    private String department;

    /** A real {@code COMPLAINTS.category_id}, or {@link #NUMERIC_ANY}. Never null. */
    @Column(name = "CATEGORY_KEY", nullable = false)
    @Builder.Default
    private Long categoryKey = NUMERIC_ANY;

    /**
     * A real {@code COMPLAINTS.ground_of_complaint_id}, or {@link #NUMERIC_ANY}.
     *
     * <p>MEASURED as {@link #NUMERIC_ANY} on every row this rollup will ever write today, because the
     * source column is populated on 0 of 4,403 complaints. The dimension is carried anyway — the brief
     * names it, {@code GROUND_OF_COMPLAINT_MASTER} exists, and a column that is in the key costs
     * nothing until it starts being written, at which point the rollup sharpens with no schema or code
     * change. Dropping it would have made adding it later a migration.
     */
    @Column(name = "GROUND_KEY", nullable = false)
    @Builder.Default
    private Long groundKey = NUMERIC_ANY;

    /**
     * {@code REGULATED_ENTITIES.entity_type} resolved from the complaint's entity, or
     * {@link #TEXT_ANY}.
     *
     * <p>The ENTITY TYPE and not the entity. {@code entity_code} is free text and documented dirty —
     * the same bank appears as {@code 'Punjab National Bank'} and {@code 'PNB'} — so keying on the code
     * would split one entity's history across spellings. The type is a small controlled vocabulary
     * (9 values seeded) and is what the brief actually asks for. Resolved through
     * {@code RegulatedEntity.nameNormalized}, which is the normalisation the entity itself applies on
     * write; an unmatched code yields the sentinel rather than being dropped.
     */
    @Column(name = "ENTITY_TYPE", nullable = false, length = 100)
    private String entityType;

    /**
     * The brief's "resolution path", as the only column that can answer it before closure.
     *
     * <p>{@code maintainability_determination} ({@code MAINTAINABLE} / {@code NON_MAINTAINABLE}), or
     * {@link #TEXT_ANY}. The brief does not name a column and there is no {@code resolution_path} in
     * {@code COMPLAINTS}. The candidates, measured:
     * <ul>
     *   <li>{@code closure_cause} ({@code RESOLVED} / {@code ADMIN_CLOSED} / {@code NON_MAINTAINABLE})
     *       is the best-populated — 875 of 877 clause-bearing rows — and is UNUSABLE as a key, because
     *       it is written at closure: 10 of 1,200 open complaints carry one. A key the request path
     *       cannot supply is a rollup nothing can read.
     *   <li>{@code conciliation_outcome} and {@code adjudication_outcome}: 0 rows each.
     *   <li>{@code maintainability_determination}: 24 of 877 closed, 26 of 1,200 open. Thin, but it is
     *       decided BEFORE closure and is therefore knowable at clause-pick time — and it separates the
     *       one genuinely distinct clause population in the data (24 of 24
     *       {@code NON_MAINTAINABLE} closures cite {@code 16(2)(a)}, against 713 of 733 unqualified
     *       CEPC closures citing {@code 15(1)(a)}).
     * </ul>
     * Thin and HONEST is preferred to well-populated and unreadable. Upper-cased on write.
     *
     * <p>NORMALISED ON WRITE, not folded on read, and this is the cross-engine trap in this area: the
     * status/action vocabularies in this database mix cases ({@code assigned} beside
     * {@code NOT_OPENED}), MySQL's {@code utf8mb4_unicode_ci} collation folds comparisons for free, and
     * Oracle does not. A read that matched verbatim would be case-insensitive in dev and case-sensitive
     * in production. So both the refresh and the read upper-case these columns in Java before they
     * reach SQL, which is also what §6.2 means by "normalise on write, not on read" — no
     * {@code UPPER(column)} wrapping an indexed column anywhere in this feature.
     */
    @Column(name = "RESOLUTION_PATH", nullable = false, length = 40)
    private String resolutionPath;

    /**
     * The clause cited, verbatim as {@code COMPLAINTS.closure_clause} spells it, e.g. {@code 15(1)(a)}.
     *
     * <p>NOT case-folded, unlike every other text column here, because a statutory citation has one
     * spelling and {@code CLOSURE_CLAUSE_MASTER.clause_code} is the authority for it. The read matches
     * these against the master's codes and silently ignores any that do not match, so a junk value in
     * the source column cannot put a clause into an officer's picker that the master does not hold.
     */
    @Column(name = "CLAUSE_CODE", nullable = false, length = 100)
    private String clauseCode;

    /** How many complaints in this cohort cited {@link #clauseCode}. The numerator. */
    @Column(name = "OCCURRENCES", nullable = false)
    private Long occurrences;

    /**
     * How many complaints the cohort holds in total, across ALL clauses. The denominator.
     *
     * <p>{@code nullable = false} because §5.1 names the failure directly: "a bare recommendation with
     * no denominator will be distrusted, correctly". Denormalised onto every row of the cohort rather
     * than derived by summing them, so the read needs no second aggregate and so a partially-refreshed
     * cohort cannot show a numerator from this pass against a denominator built from the last.
     */
    @Column(name = "COHORT_TOTAL", nullable = false)
    private Long cohortTotal;

    /**
     * When the refresh that wrote this row ran.
     *
     * <p>Read by nothing in the request path. It exists so a rollup that quietly stopped refreshing is
     * diagnosable, which is the one failure a scheduled job has that an endpoint does not: stale counts
     * look exactly like correct counts. It is also the stale sweep's predicate.
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

    /**
     * How many of the six key dimensions are SPECIFIC rather than wildcarded.
     *
     * <p>The read's first ranking rule: a cohort that matches the complaint on more dimensions is
     * better evidence about it than one that matches on fewer, whatever the counts say. Scheme version
     * is excluded because it is never a wildcard, so counting it would add a constant to every row.
     */
    @Transient
    public int specificity() {
        int score = 0;
        if (!TEXT_ANY.equals(department)) {
            score++;
        }
        if (categoryKey != null && categoryKey != NUMERIC_ANY) {
            score++;
        }
        if (groundKey != null && groundKey != NUMERIC_ANY) {
            score++;
        }
        if (!TEXT_ANY.equals(entityType)) {
            score++;
        }
        if (!TEXT_ANY.equals(resolutionPath)) {
            score++;
        }
        return score;
    }
}
