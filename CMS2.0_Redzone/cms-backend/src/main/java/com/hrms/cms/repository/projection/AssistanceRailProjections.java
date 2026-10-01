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
     * The five columns the rail needs from the complaint it is rendering beside.
     *
     * <p>Deliberately carries no complainant NAME, no subject and no description. The rail reports
     * counts about neighbouring complaints, and the officer is already looking at this one, so pulling
     * identifying text in would widen what a rail failure or a mis-scoped log could disclose for no
     * gain. {@code complainantEmail} is here because it is the join key for the history prior and is
     * never echoed back in the response.
     */
    public record RailContext(String complaintNumber,
                              String complainantEmail,
                              String entityCode,
                              String closureClause,
                              Long categoryId) {
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
}
