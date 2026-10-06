package com.hrms.cms.repository;

import com.hrms.cms.entity.AssistanceNextAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.QueryHint;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The next-action rollup: one keyed read for the rail, and the refresh job's write paths.
 *
 * <h2>The read is ONE equality seek, and that is the point of the table existing</h2>
 * §5.3 requires every prior to be "answerable by a single keyed lookup" and §6.2 forbids computing a
 * rollup on request. {@link #findCohort} seeks the {@code UK_ANA_COHORT} unique constraint on all three
 * key columns — the equivalent {@code GROUP BY} over {@code COMPLAINT_TIMELINE} would scan 17,433 rows
 * on every staff screen load, which is why the aggregation happens on a schedule instead.
 *
 * <p>The REQUEST path therefore touches {@code COMPLAINT_TIMELINE} not at all. No finder here reads it.
 *
 * <h2>The rail reads CANDIDATES and chooses in Java; the refresh reads ONE row</h2>
 * {@link #findCohort} is the refresh's upsert seek: it knows the exact cohort it computed, so it asks
 * for exactly that row. {@link #findRailCandidates} cannot, and the reason is the caller rather than the
 * table — a staff token carries a SET of roles (plus Keycloak's own {@code offline_access} and
 * {@code default-roles-*}, which match no cohort), and the rail does not know which of them the officer
 * is acting as. One {@code IN} over both the roles and the two category keys is therefore a single
 * index range on {@code UK_ANA_COHORT}'s leading column, where the alternative is two seeks per role —
 * up to sixteen statements on a screen load, to answer one question.
 *
 * <p>The PREFERENCE between the rows that come back is deliberately NOT an {@code ORDER BY}. It is
 * three rules — category-specific over agnostic, then better-evidenced, then alphabetical — and
 * {@code AssistanceRailService} applies them where a reader can see them and a unit test can pin them,
 * rather than leaving an officer's suggestion to be decided by a clause in a string.
 *
 * <h2>Why the timeout hint is on a read of a 34-row table</h2>
 * It follows {@code ComplaintRepository}'s convention deliberately rather than because this table is
 * large. The rail renders on every staff screen load; a lock wait or an unexpected plan on a small
 * table can still stall a request, and the rail's contract is to degrade to silence rather than to
 * hold up the screen beside it.
 */
public interface AssistanceNextActionRepository extends JpaRepository<AssistanceNextAction, Long> {

    /** Shared with {@code ComplaintRepository}; spelled out because interfaces cannot inherit it. */
    String QUERY_TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    /**
     * The rail read budget, matching the dashboard aggregates' 5s.
     *
     * <p>The same number for a much smaller query, on purpose: the value is a CEILING past which the
     * caller would rather have no answer, not an expectation of how long the query takes. A tighter
     * bound here would make the signal flap under incidental contention.
     */
    String RAIL_TIMEOUT_MS = "5000";

    /**
     * One exact cohort, for the REFRESH's insert-or-update decision.
     *
     * <p>Plan: unique-key seek on {@code UK_ANA_COHORT}, one row or none. {@code categoryKey} must be
     * either a real category id or {@link AssistanceNextAction#CATEGORY_AGNOSTIC} — never null, because
     * a NULL in a composite key is not comparable and the seek would miss the sentinel row that does
     * exist.
     *
     * <p>No timeout hint, unlike {@link #findRailCandidates}, and the asymmetry is the point: a request
     * would rather give up than stall the screen beside it, whereas the job would rather wait than skip
     * a cohort and leave the rollup reporting last hour's winner. Borrowing the request budget here
     * would make the refresh abandon its work under exactly the contention it should tolerate.
     */
    @Query("SELECT a FROM AssistanceNextAction a "
            + "WHERE a.fromStatus = :fromStatus AND a.performedByRole = :role "
            + "AND a.categoryKey = :categoryKey")
    Optional<AssistanceNextAction> findCohort(@Param("fromStatus") String fromStatus,
                                              @Param("role") String role,
                                              @Param("categoryKey") Long categoryKey);

    /**
     * Every cohort that could possibly apply to this caller on this complaint.
     *
     * <p>Plan: index range on {@code UK_ANA_COHORT}'s leading {@code FROM_STATUS} column, filtered on
     * the two {@code IN} lists. Returns at most {@code roles.size() x 2} rows and in practice one or
     * two, because a given status/role pair has a category-specific cohort only where the register has
     * enough categorised history to support one.
     *
     * <p>ONE statement rather than a seek per candidate. A staff token carries a SET of roles — several
     * real ones plus Keycloak's {@code offline_access} and {@code default-roles-cms}, which match no
     * cohort and cannot be filtered out here without this query encoding the realm's role vocabulary —
     * and the rail does not know which of them the officer is acting as. Looping seeks would issue up to
     * sixteen statements on a screen load to answer one question.
     *
     * @param categoryKeys the complaint's category AND {@link AssistanceNextAction#CATEGORY_AGNOSTIC},
     *                     or just the sentinel when the complaint has no category. Both are passed
     *                     together so the fallback costs no second round trip; the caller chooses
     *                     between the rows that come back.
     */
    @Query("SELECT a FROM AssistanceNextAction a "
            + "WHERE a.fromStatus = :fromStatus AND a.performedByRole IN :roles "
            + "AND a.categoryKey IN :categoryKeys")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = RAIL_TIMEOUT_MS))
    List<AssistanceNextAction> findRailCandidates(@Param("fromStatus") String fromStatus,
                                                  @Param("roles") Collection<String> roles,
                                                  @Param("categoryKeys") Collection<Long> categoryKeys);

    /**
     * Deletes cohorts the latest refresh did not rewrite.
     *
     * <p>Needed because the refresh UPSERTS rather than truncating-and-reloading — a truncate would
     * leave the rail silent for the duration of every refresh, which is a self-inflicted outage on a
     * feature whose whole contract is to be there when the screen loads. The cost of upserting is that
     * a cohort which has FALLEN BELOW the sample or confidence floor would otherwise keep its last
     * computed row forever, and the rail would go on reporting a prior the data no longer supports.
     *
     * <p>Keyed on {@code REFRESHED_AT} rather than on a list of surviving ids: the job stamps every row
     * it writes with ONE timestamp taken at the start of the pass, so "older than this run" is exactly
     * "not rewritten by this run" — no id list to carry and no second query to build it.
     *
     * <p>CALLED ONLY AFTER A COMPLETE PASS. That is a requirement on the caller, not a property of this
     * query: a pass that aborted halfway has stamped only the cohorts it reached, so sweeping on its
     * stamp would delete every cohort it had not got to yet and the rail would go quiet on them until
     * the next successful run. {@code AssistanceNextActionRefreshService} therefore runs the sweep after
     * the upsert loop returns normally and skips it on failure.
     */
    // @Transactional on the repository method, not inherited from a caller: the refresh service
    // deliberately runs no transaction of its own (a self-invoked @Transactional would be inert), so a
    // modifying query called from it would otherwise fail with TransactionRequiredException. Declaring
    // it here means the boundary exists wherever this is called from.
    @Transactional
    @Modifying
    @Query("DELETE FROM AssistanceNextAction a WHERE a.refreshedAt < :staleBefore")
    int deleteStale(@Param("staleBefore") LocalDateTime staleBefore);
}
