package com.hrms.cms.repository.projection;

import java.time.LocalDateTime;

/**
 * Constructor-expression carriers for the Tier 1 assistance-rail priors (Brief 21).
 *
 * <p>Same reasoning as {@link DashboardProjections}: {@code COMPLAINTS} has ~105 columns including six
 * {@code TEXT} bodies, and the rail renders on every staff screen load, so hydrating a
 * {@code Complaint} entity to read five scalars would be the most expensive part of the response.
 * JPQL {@code SELECT new} rather than native SQL because the deployed profiles run OracleDialect and
 * only dev-local runs MySQL.
 */
public final class AssistanceRailProjections {

    private AssistanceRailProjections() {
    }

    /**
     * The six columns the rail needs from the complaint it is rendering beside.
     *
     * <p>Deliberately carries no complainant NAME, no subject and no description. The rail reports
     * counts about neighbouring complaints, and the officer is already looking at this one, so pulling
     * identifying text in would widen what a rail failure or a mis-scoped log could disclose for no
     * gain. {@code complainantEmail} is here because it is the join key for the history prior and is
     * never echoed back in the response.
     *
     * <p>{@code status} is the complaint's CURRENT status, and it is the join key for the next-action
     * prior: the rollup is keyed on the {@code from_status} of past events, so "what usually happens
     * next" is a lookup on where this complaint is sitting now. Measured caveat worth knowing — the
     * timeline's status vocabulary and {@code COMPLAINTS.status} overlap but are not identical
     * ({@code assigned}, {@code in_progress} and {@code closed} appear in both, while the timeline also
     * carries {@code NOT_OPENED}/{@code OPENED} that no complaint is ever in), so a status with no
     * cohort is a NORMAL outcome and the signal is simply silent.
     */
    public record RailContext(String complaintNumber,
                              String complainantEmail,
                              String entityCode,
                              String closureClause,
                              Long categoryId,
                              String status) {
    }

    /**
     * One closed complaint's filed and closed timestamps, for the median-duration prior.
     *
     * <p>A pair rather than a pre-computed day count because the subtraction is not portable: MySQL
     * spells it {@code TIMESTAMPDIFF} and Oracle returns an INTERVAL from plain subtraction, and JPQL
     * has no portable date-difference function. So the two timestamps come back and the arithmetic
     * happens in Java, which is a handful of subtractions over a capped row set.
     */
    public record ClosureWindow(LocalDateTime filedAt, LocalDateTime closedAt) {
    }

    /**
     * One timeline event reduced to the four things the next-action rollup counts, plus its id.
     *
     * <p>Used ONLY by the scheduled refresh ({@code AssistanceNextActionRefreshService}), never on a
     * request. It is listed here beside the read projections because it is the same feature, and
     * because keeping the refresh's shape visible next to the rail's makes it obvious that the request
     * path touches {@code COMPLAINT_TIMELINE} not at all.
     *
     * <p>{@code timelineId} is carried for KEYSET PAGING and for nothing else: the refresh walks the
     * table {@code WHERE t.id > :afterId ORDER BY t.id}, so each page needs the last id it saw. An
     * offset-based page would re-read rows as the table grows underneath a long refresh, and would
     * degrade quadratically on the deep pages — which on an append-only log is where all the recent
     * history lives.
     *
     * <p>{@code categoryId} comes from the complaint by an id join and is NULLABLE for two distinct
     * reasons that are both normal: the complaint may carry no category (the common case — 722 of
     * 16,989 joinable rows have one), or the complaint may be gone entirely. The join is therefore an
     * outer one and the refresh counts such a row toward the category-agnostic cohort only.
     */
    public record TimelineAction(Long timelineId,
                                 String fromStatus,
                                 String performedByRole,
                                 String action,
                                 Long categoryId) {
    }

    /**
     * The officer's whole open queue reduced to three numbers, for the deadline-triage prior (§5.3.4).
     *
     * <h3>This is the one record here that describes NO COMPLAINT</h3>
     * It is a statement about the CALLER'S QUEUE — "3 of your 14 cases breach within 48h" — and it is
     * the reason the rail's per-complaint contract had to grow a queue-wide signal. The complaint on
     * screen is irrelevant to it; the only input is the resolved officer id.
     *
     * <h3>There are no rows to cap, because nothing is fetched</h3>
     * All three fields come back from ONE aggregate statement:
     * {@code SELECT COUNT(c), SUM(CASE ...), SUM(CASE ...)} over a covering index. Nothing is hydrated
     * into heap and there is no per-row work in Java, which is why this record has no {@code Pageable}
     * sibling. The recorded anti-pattern it avoids is {@code SeniorDashboardService}'s five
     * {@code findAll()} hydrates — "3 of your 14 breach" computed by loading 14 complaints with their
     * six {@code TEXT} bodies and filtering in Java would be the same defect in a new place.
     *
     * <h3>{@code atRisk} INCLUDES the already-overdue, and {@code overdue} is a subset of it</h3>
     * An officer triaging their morning does not need "breaching soon" and "already breached" as two
     * separate sentences, and a horizon count that EXCLUDED the overdue would undercount exactly the
     * cases that matter most — a case three days past its deadline would vanish from the signal while a
     * case due tomorrow stayed in it. So {@code overdue} is a qualifier on the same number, never a
     * second bucket: a caller that added the two would double-count every overdue case.
     *
     * @param queueSize open complaints assigned to this officer — the DENOMINATOR, and the whole reason
     *                  the signal is sayable. §5.1: "a bare recommendation with no denominator will be
     *                  distrusted, correctly."
     * @param atRisk    of those, how many carry a deadline at or before the horizon, overdue included
     * @param overdue   of {@code atRisk}, how many are already past their deadline
     */
    public record DeadlineTriage(long queueSize, long atRisk, long overdue) {

        /** Nothing in the queue. The shape a failed or ownerless lookup degrades to. */
        public static DeadlineTriage none() {
            return new DeadlineTriage(0, 0, 0);
        }
    }

    /**
     * The four columns that key the entity-pattern rollup read (§5.3.5), for the complaint on screen.
     *
     * <h3>A SECOND query rather than four more fields on {@link RailContext}, and that is deliberate</h3>
     * {@code RailContext} is constructed positionally at sixteen sites in
     * {@code AssistanceRailServiceTest}, so widening it would be a sixteen-file edit to the tests of four
     * priors that do not want these columns — and it would be a widening of what the OTHER priors can
     * see, which is the opposite of the direction {@code RailContext}'s own javadoc argues for ("pulling
     * identifying text in would widen what a rail failure could disclose for no gain"). The cost is one
     * extra seek on {@code idx_complaint_number}, which is unique, on a row the request is already
     * fetching — measured in the findings, and it is not the expensive part of anything.
     *
     * <h3>{@code department} IS THE TENANCY FENCE AND IS READ FROM THE COMPLAINT, NOT FROM THE REQUEST</h3>
     * This is the field §4 is about. It comes from {@code COMPLAINTS.department} on the complaint the
     * officer already has open — never from a query parameter, a body, or a header, because
     * {@code EmailSyndicationApiController:451} is the recorded defect where a caller-supplied scope
     * meant an omitted one disclosed everything. {@code AssistanceRailService} then ANDs it into the
     * rollup read, so the count an officer sees is bounded by the department of the case in front of
     * them.
     *
     * <h3>What each field is NOT</h3>
     * No complainant email, no name, no subject, no clause, no complaint text. The entity-pattern signal
     * is a count about a REGULATED ENTITY and this record carries only what keys that count — so a
     * failure, a log line or a widened response cannot leak a complainant through this path. Four
     * nullables and nothing else.
     *
     * @param entityCode  RAW {@code COMPLAINTS.entity_code}. Normalised by the caller through
     *                    {@code AssistanceEntityAliasNormaliser} before it reaches the query, never by
     *                    wrapping the indexed column in a function. Null or blank on 290 of 4,403 rows,
     *                    in which case the signal is silent.
     * @param department  {@code COMPLAINTS.department}. Populated on 4,380 of 4,403. When it is absent
     *                    the signal is SILENT rather than falling back to an unscoped read — the
     *                    restrictive default of §4, applied to the one case where the fence cannot be
     *                    established.
     * @param officeCode  {@code COMPLAINTS.rbio_office_code}, populated on 410 of 4,403. Null is NORMAL
     *                    and resolves to the office-agnostic rollup row rather than to silence.
     * @param groundId    {@code COMPLAINTS.ground_of_complaint_id}, populated on <b>0 of 4,403</b>. The
     *                    brief's prescribed dimension, and empty — so in practice this is always null and
     *                    the read always falls back to the ground-agnostic row. Carried anyway so that
     *                    the day the column starts being written the signal sharpens with no code change.
     * @param filedAt     {@code COMPLAINTS.created_at}, which decides the quarter. Null on no rows today;
     *                    a null makes the signal silent, because a window needs a window.
     */
    public record EntityPatternContext(String entityCode,
                                       String department,
                                       String officeCode,
                                       Long groundId,
                                       LocalDateTime filedAt) {
    }

    /**
     * One complaint reduced to the six things the entity-pattern rollup counts, plus its id.
     *
     * <p>Used ONLY by the scheduled refresh ({@code AssistanceEntityPatternRefreshService}), never on a
     * request. Listed here beside the read projections for the reason {@link TimelineAction} gives:
     * keeping the refresh's shape visible next to the rail's makes it obvious that the request path
     * aggregates nothing.
     *
     * <p>{@code complaintId} is carried for KEYSET PAGING and for nothing else — the refresh walks
     * {@code WHERE c.id > :afterId ORDER BY c.id}, so each page needs the last id it saw. An offset page
     * would re-read rows as the register grows underneath a long refresh and would degrade quadratically
     * on the deep pages.
     *
     * <p>{@code status} comes back RAW rather than as a boolean, and the arithmetic happens in Java
     * against {@code RbioStatusVocabulary}. That is the point: there were previously two hardcoded copies
     * of the closed-status list in this codebase and they DISAGREED, so a complaint closed by an award
     * counted as open to one of them. Encoding the list into this query would mint a third copy, and a
     * rollup whose definition of "open" drifted from the rest of the system would report live cases that
     * are closed — a false statement about a regulated entity rather than a missing one.
     */
    public record EntityPatternRow(Long complaintId,
                                   String entityCode,
                                   String department,
                                   String officeCode,
                                   Long groundId,
                                   String status,
                                   LocalDateTime filedAt) {
    }
}
