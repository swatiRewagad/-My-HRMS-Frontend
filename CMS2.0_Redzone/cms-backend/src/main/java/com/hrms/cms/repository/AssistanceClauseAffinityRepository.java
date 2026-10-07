package com.hrms.cms.repository;

import com.hrms.cms.entity.AssistanceClauseAffinity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.QueryHint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The closure-clause affinity rollup: one keyed read per cohort, and the refresh job's write paths.
 *
 * <h2>The read is ONE index range, and that is why the table exists</h2>
 * §5.3's governing rule is that every prior must be "answerable by a single keyed lookup", and §6.2
 * forbids computing a rollup on request. {@link #findCohortClauses} seeks the {@code UK_ACA_COHORT}
 * unique constraint on its six leading key columns and returns that cohort's clause distribution — at
 * most {@link AssistanceClauseAffinity#MAX_CLAUSES_PER_COHORT} rows. The equivalent request-time
 * {@code GROUP BY} would scan the clause-bearing slice of {@code COMPLAINTS} (877 rows today,
 * unbounded later) every time an officer opened a closure form.
 *
 * <p>The REQUEST path therefore touches {@code COMPLAINTS} not at all. No finder here reads it.
 *
 * <h2>Why the read asks for a whole distribution and the next-action rollup asks for one row</h2>
 * {@code AssistanceNextActionRepository.findCohort} returns an {@code Optional} because the rail shows
 * ONE suggestion. §5.3.2's deliverable is an ORDERING of the clause {@code <select>}, so the read
 * genuinely needs every clause in the cohort. It is still one statement against one index, not an
 * aggregate — the counting happened on the schedule.
 *
 * <h2>Why the ladder is several calls and not one query</h2>
 * {@code ClosureClauseRecommendationService} walks from the most specific cohort to the least and stops
 * at the first that answers, which is up to 4 seeks in the worst case rather than one {@code IN} over
 * every combination. That shape is deliberate: the {@code IN} would return rows from several
 * specificity levels at once and the service would have to choose between them anyway, so the only
 * thing it would save is round trips — while costing the property that matters, which is that the
 * service STOPS at the first level that clears the floors and never mixes evidence from two levels in
 * one ordering. The common case is 1 or 2 seeks, each a unique-index range on a table with one row per
 * (cohort, clause).
 *
 * <h2>Every query here declares a cap AND a timeout</h2>
 * Per §6.2. The cap is a {@code Pageable} the caller must supply (there is no unbounded finder on this
 * interface at all) and the timeout is the same 5s ceiling {@code AssistanceNextActionRepository} uses.
 * The ceiling is not an expectation of how long the query takes — it is the point past which the caller
 * would rather render the clause list in its original order than hold up a closure form.
 */
public interface AssistanceClauseAffinityRepository extends JpaRepository<AssistanceClauseAffinity, Long> {

    /** Shared with {@code AssistanceNextActionRepository}; spelled out because interfaces cannot inherit it. */
    String QUERY_TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    /**
     * The read budget, matching the next-action rail's 5s.
     *
     * <p>A CEILING past which the caller would rather have no recommendation, not a latency target. A
     * tighter bound would make the ordering flap between "ranked" and "original" under incidental
     * contention, which is worse than being consistently unranked: an officer who sees a different
     * order on each load cannot build any expectation of where a clause will be.
     */
    String READ_TIMEOUT_MS = "5000";

    /**
     * One cohort's whole clause distribution, by exact key.
     *
     * <p>Plan: index range on {@code UK_ACA_COHORT}'s six leading columns, returning one row per clause
     * the cohort cited. All six are equality predicates, so this is a seek and not a scan, and the
     * {@code CLAUSE_CODE} tail of the key makes the rows contiguous.
     *
     * <p>Text parameters must arrive ALREADY UPPER-CASED and the sentinels must be
     * {@link AssistanceClauseAffinity#TEXT_ANY} / {@link AssistanceClauseAffinity#NUMERIC_ANY} rather
     * than null — two separate reasons, both load-bearing. Upper-cased because a {@code UPPER(column)}
     * wrap would defeat the index (§6.2) and because MySQL folds case for free while Oracle does not,
     * so a verbatim comparison would be case-insensitive in dev and sensitive in production. Sentinels
     * rather than nulls because {@code = null} is never true, so a nullable key column would make the
     * wildcard rows unreachable by any seek.
     *
     * <p>{@code ORDER BY} is deliberately NOT the ranking. It orders by clause code purely so the rows
     * arrive in a deterministic sequence; the RANKING is three stated rules applied in
     * {@code ClosureClauseRecommendationService}, where a reader can see them and a unit test can pin
     * them, rather than being decided by a clause inside a string.
     *
     * @param page the §6.2 row cap. Callers pass
     *             {@link AssistanceClauseAffinity#MAX_CLAUSES_PER_COHORT}; there is no unbounded
     *             overload, so a cap cannot be forgotten at a call site.
     */
    @Query("SELECT a FROM AssistanceClauseAffinity a "
            + "WHERE a.schemeVersion = :scheme AND a.department = :department "
            + "AND a.categoryKey = :categoryKey AND a.groundKey = :groundKey "
            + "AND a.entityType = :entityType AND a.resolutionPath = :resolutionPath "
            + "ORDER BY a.clauseCode")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = READ_TIMEOUT_MS))
    List<AssistanceClauseAffinity> findCohortClauses(@Param("scheme") String scheme,
                                                     @Param("department") String department,
                                                     @Param("categoryKey") Long categoryKey,
                                                     @Param("groundKey") Long groundKey,
                                                     @Param("entityType") String entityType,
                                                     @Param("resolutionPath") String resolutionPath,
                                                     Pageable page);

    /**
     * One exact (cohort, clause) row, for the REFRESH's insert-or-update decision.
     *
     * <p>Plan: unique-key seek on all seven key columns, one row or none.
     *
     * <p>No timeout hint, unlike {@link #findCohortClauses}, and the asymmetry is the point: a request
     * would rather give up than stall the form beside it, whereas the job would rather wait than skip a
     * cohort and leave the rollup reporting last hour's distribution. Borrowing the request budget here
     * would make the refresh abandon its work under exactly the contention it should tolerate.
     */
    @Query("SELECT a FROM AssistanceClauseAffinity a "
            + "WHERE a.schemeVersion = :scheme AND a.department = :department "
            + "AND a.categoryKey = :categoryKey AND a.groundKey = :groundKey "
            + "AND a.entityType = :entityType AND a.resolutionPath = :resolutionPath "
            + "AND a.clauseCode = :clauseCode")
    Optional<AssistanceClauseAffinity> findCohortClause(@Param("scheme") String scheme,
                                                        @Param("department") String department,
                                                        @Param("categoryKey") Long categoryKey,
                                                        @Param("groundKey") Long groundKey,
                                                        @Param("entityType") String entityType,
                                                        @Param("resolutionPath") String resolutionPath,
                                                        @Param("clauseCode") String clauseCode);

    /**
     * Deletes rows the latest refresh did not rewrite.
     *
     * <p>Needed because the refresh UPSERTS rather than truncating-and-reloading — a truncate would
     * leave every closure form unranked for the duration of every refresh, which is a self-inflicted
     * degradation on a feature whose contract is to be there when the form opens. The cost of upserting
     * is that a (cohort, clause) pair which has fallen below the floors, or a clause that is no longer
     * cited at all, would otherwise keep its last computed row forever and go on being recommended.
     *
     * <p>Keyed on {@code REFRESHED_AT} rather than on a list of surviving ids: the job stamps every row
     * it writes with ONE timestamp taken at the start of the pass, so "older than this run" is exactly
     * "not rewritten by this run" — no id list to carry and no second query to build it.
     *
     * <p>CALLED ONLY AFTER A COMPLETE PASS. That is a requirement on the caller, not a property of this
     * query: a pass that aborted halfway has stamped only the cohorts it reached, so sweeping on its
     * stamp would delete every cohort it had not got to and the picker would go unranked on them until
     * the next successful run. {@code AssistanceClauseAffinityRefreshService} therefore runs the sweep
     * after the upsert loop returns normally, and skips it on failure.
     */
    // @Transactional on the REPOSITORY method, not inherited from a caller. The refresh service
    // deliberately runs no transaction of its own — its recompute() is invoked through `this` from
    // refresh(), and Spring's proxy does not intercept a self-invocation, so a @Transactional there
    // would be INERT while reading as a boundary. Declared here, where the proxy IS the bean, so the
    // boundary exists wherever this is called from. Same resolution as
    // AssistanceNextActionRepository#deleteStale.
    @Transactional
    @Modifying
    @Query("DELETE FROM AssistanceClauseAffinity a WHERE a.refreshedAt < :staleBefore")
    int deleteStale(@Param("staleBefore") LocalDateTime staleBefore);
}
