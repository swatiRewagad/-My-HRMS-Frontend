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
}
