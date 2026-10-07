package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One cohort's most-common next action, precomputed from the timeline (Brief 21 §5.3.1).
 *
 * <h2>What a row MEANS, said precisely</h2>
 * "Of the {@link #cohortTotal} times someone in {@link #performedByRole} acted on a complaint sitting
 * at {@link #fromStatus}, {@link #occurrences} of them did {@link #action}." Nothing more. It is a
 * COUNT of what the register already did, which is why this stays inside the brief's Tier 1 — there is
 * no inference over case text, no model artifact, and no judgement about whether the action was right.
 *
 * <h2>The denominator is part of the fact, not a nicety</h2>
 * {@link #occurrences} and {@link #cohortTotal} are both {@code nullable = false} because §5.1 names
 * the failure directly: "a bare recommendation with no denominator will be distrusted, correctly".
 * A schema that permitted the numerator alone would permit the rail to say "usually ACCEPT" with
 * nothing behind it, and an officer has no way to tell that from a figure drawn from 200 cases.
 *
 * <h2>CATEGORY_KEY is a SENTINEL, and 0 is not a magic number by accident</h2>
 * The brief keys this on (from_status, performed_by_role, category). Measured against the dev
 * database that key is nearly empty — only 722 of 16,989 joinable timeline rows reach a complaint
 * carrying a category, giving 5 usable cohorts against 34 for the two-column key. So the category is
 * still part of the key, but {@code 0} means "this cohort is not category-specific": the refresh
 * writes a category-specific row wherever the data supports one AND a category-agnostic row for every
 * cohort, and the read prefers the specific row. Precision where the data allows, coverage everywhere
 * else, and the fallback is a stored row rather than a second query shape.
 *
 * <p>NOT {@code null}, which would have been the natural spelling. A composite UNIQUE key containing
 * a NULL does not prevent duplicate rows on MySQL or on Oracle, so a NULL category would make the key
 * non-idempotent and every refresh would append a fresh set of agnostic rows instead of updating the
 * existing one. Verified that no real {@code category_id} is 0 — the column is AUTO_INCREMENT from 1.
 *
 * <h2>Only the WINNER is stored</h2>
 * Not one row per (cohort, action). §5.3 requires every prior to be "answerable by a single keyed
 * lookup", and the rail shows one suggestion with its denominator, so the rest of the distribution
 * would be rows nothing reads — and would turn an equality seek into a sort.
 *
 * <h2>Advisory, and the service must keep it that way</h2>
 * This table is mined from what HAPPENED, not from what is permitted. The RBIO machine's from-status
 * rules are advertisement only, so a historically-common action can be one the workflow would now
 * refuse. That makes the brief's "suggest, highlight, do not auto-select" a correctness requirement
 * here rather than a UX preference: {@code workflow-action-bar} commits real transitions.
 *
 * @see com.hrms.cms.service.AssistanceNextActionRefreshService the only writer
 */
@Entity
@Table(name = "ASSISTANCE_NEXT_ACTION",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_ANA_COHORT",
                columnNames = {"FROM_STATUS", "PERFORMED_BY_ROLE", "CATEGORY_KEY"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistanceNextAction {

    /** {@code CATEGORY_KEY} for a cohort that is not category-specific. See the class javadoc. */
    public static final long CATEGORY_AGNOSTIC = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The status the complaint was sitting at. Width matched to {@code COMPLAINT_TIMELINE.from_status}
     * so a value cannot be silently truncated on the way into the rollup.
     *
     * <p>Stored VERBATIM, not case-folded. The timeline's own values are inconsistent
     * ({@code assigned} alongside {@code NOT_OPENED}), and folding here would merge two vocabularies
     * the rest of the system treats as distinct. The measured consequence is the same one
     * {@code ComplaintRepository} records for {@code entity_code}: MySQL's {@code ai_ci} collation
     * folds this comparison for free and Oracle does not, so the read is case-insensitive on one
     * engine and sensitive on the other.
     */
    @Column(name = "FROM_STATUS", nullable = false, length = 30)
    private String fromStatus;

    /** The acting role, as the timeline recorded it. Width matched to the source column. */
    @Column(name = "PERFORMED_BY_ROLE", nullable = false, length = 50)
    private String performedByRole;

    /** A real {@code COMPLAINTS.category_id}, or {@link #CATEGORY_AGNOSTIC}. Never null. */
    @Column(name = "CATEGORY_KEY", nullable = false)
    @Builder.Default
    private Long categoryKey = CATEGORY_AGNOSTIC;

    /**
     * The action that most often followed, as the RAW timeline value ({@code ACCEPT},
     * {@code SUBMIT_FOR_REVIEW}).
     *
     * <p>A machine key rather than prose, because the client matches it against the action bar's own
     * ids to decide which button to highlight. The rail's sentence comes from the i18n bundle; nothing
     * here is displayed directly.
     */
    @Column(name = "ACTION", nullable = false, length = 50)
    private String action;

    /** How many times {@link #action} was taken from this cohort. The numerator. */
    @Column(name = "OCCURRENCES", nullable = false)
    private Long occurrences;

    /** How many times ANY action was taken from this cohort. The denominator. */
    @Column(name = "COHORT_TOTAL", nullable = false)
    private Long cohortTotal;

    /**
     * When the refresh that wrote this row ran.
     *
     * <p>Read by nothing in the request path. It exists so that a rollup which quietly stopped
     * refreshing is diagnosable, which is the one failure a scheduled job has that an endpoint does
     * not: stale counts look exactly like correct counts.
     */
    @Column(name = "REFRESHED_AT", nullable = false)
    private LocalDateTime refreshedAt;

    /** The winner's share of its cohort, 0..1. Derived, never stored — the two counts are the truth. */
    @Transient
    public double confidence() {
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
}
