package com.hrms.cms.repository;

import com.hrms.cms.entity.AssistanceClausePrior;
import org.springframework.data.domain.Pageable;
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
 * The closure-clause prior: one keyed read for the picker, and the refresh job's scan and write paths
 * (Brief 21 §5.3.2).
 *
 * <h2>The read is ONE index range, and that is the point of the table existing</h2>
 * §5.3 requires every prior to be "answerable by a single keyed lookup" and §6.2 forbids computing a
 * rollup on request. {@link #findRankedCandidates} is a range on {@code UK_ACP_COHORT_CLAUSE}'s three
 * leading columns. MEASURED plan, against a scratch table holding the eight rows this rollup would
 * carry on today's register:
 *
 * <pre>
 * EXPLAIN SELECT * FROM ASSISTANCE_CLAUSE_PRIOR
 *  WHERE DEPARTMENT='CEPC' AND CATEGORY_KEY IN (1,0) AND ENTITY_KEY IN ('TEST BANK LTD','*');
 *   type=range  key=UK_ACP_COHORT_CLAUSE  key_len=292  rows=4  filtered=100.00
 *   used_key_parts=[DEPARTMENT, CATEGORY_KEY, ENTITY_KEY]   Extra='Using index condition'
 * </pre>
 *
 * The equivalent {@code GROUP BY} over {@code COMPLAINTS} examines 877 clause-bearing rows and sorts
 * them, on every closure screen a staff user opens. That is why the aggregation happens on a schedule.
 *
 * <h2>There is deliberately no ORDER BY on the request-path query</h2>
 * Adding one turned the measured plan above into {@code Extra='Using index condition; Using filesort'}.
 * More importantly the preference between the rows that come back is not expressible as a single sort
 * key: it is three rules — narrower cohort over broader, then better-evidenced, then alphabetical by
 * clause code — and {@code ClauseRecommendationService} applies them where a reader can see them and a
 * unit test can pin them, over at most a handful of rows. A clause inside a string is the wrong place
 * to decide what an officer is shown beside a control that commits a real closure.
 *
 * <h2>Why both sentinel keys are passed in the SAME query</h2>
 * {@code categoryKeys} and {@code entityKeys} each carry the complaint's real value AND the sentinel,
 * so the specific cohort and its fallback come back together. The alternative is up to four seeks
 * (specific/specific, specific/agnostic, agnostic/specific, agnostic/agnostic) on a screen load, to
 * answer one question. One range over a composite index costs less than four seeks and cannot return a
 * torn mixture of two different refresh passes.
 *
 * <h2>Row caps and timeouts on EVERY query here (§6.2)</h2>
 * The two read queries declare {@link #MAX_CANDIDATE_ROWS} via {@code Pageable} and
 * {@link #RAIL_TIMEOUT_MS} via the {@code jakarta.persistence.query.timeout} hint; the refresh scan is
 * keyset-paged with its own cap and its own, longer budget. The constants are spelled out rather than
 * imported because interfaces cannot inherit them — the same reason
 * {@code AssistanceNextActionRepository} spells them out.
 */
public interface ClauseRecommendationRepository extends JpaRepository<AssistanceClausePrior, Long> {

    /** Shared with {@code ComplaintRepository}; spelled out because interfaces cannot inherit it. */
    String QUERY_TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    /**
     * The request-path budget, matching the dashboard aggregates' and the rail's 5s.
     *
     * <p>The same number for a much smaller query, on purpose: it is a CEILING past which the caller
     * would rather have no answer, not an expectation of how long the query takes. A tighter bound
     * would make the annotation flap under incidental contention, and a picker whose ordering changes
     * between two focus events is worse than one that never reorders.
     */
    String RAIL_TIMEOUT_MS = "5000";

    /**
     * The refresh scan's budget, an order of magnitude longer than the request path's.
     *
     * <p>The asymmetry is the point: a request would rather give up than stall the screen beside it,
     * whereas the job would rather wait than abandon a pass and leave the rollup reporting last hour's
     * distribution. Still bounded, because §6.2 requires a timeout on every query and an unbounded scan
     * of {@code COMPLAINTS} is exactly the thing a lock wait could hold open all night.
     */
    String REFRESH_TIMEOUT_MS = "60000";

    /**
     * Hard cap on rows the request path will accept from one lookup (§6.2's "declared row cap").
     *
     * <p>Sized from the constraint rather than from the data: four cohorts can match (two category keys
     * x two entity keys) and {@code ClauseRecommendationRefreshService.MAX_RANKED_PER_COHORT} is 5, so
     * 20 is the arithmetic maximum and this cap can only bite if that constant grows without this one.
     * It is here so that a corrupt or hand-edited rollup cannot turn a screen load into an unbounded
     * fetch — the cap is a guarantee about the QUERY, not a belief about the table.
     */
    int MAX_CANDIDATE_ROWS = 20;

    /**
     * Every prior row that could apply to this complaint, specific cohorts and fallbacks together.
     *
     * <p>Plan: index range on {@code UK_ACP_COHORT_CLAUSE} — see the interface javadoc for the measured
     * {@code EXPLAIN}. Returns at most {@code 4 x MAX_RANKED_PER_COHORT} rows and in practice two to
     * four, clamped by {@link #MAX_CANDIDATE_ROWS} through the {@code Pageable}.
     *
     * <p>No role filtering here, and that is deliberate rather than an omission.
     * {@code CLOSURE_CLAUSE_MASTER.restricted_to_roles} is a comma-separated column, so a {@code LIKE}
     * against it would match {@code ADMIN} inside {@code RBIO_ADMIN} and quietly widen every
     * administrator-only clause — the exact trap {@code ClosureClauseMasterRepository} documents. The
     * service splits it and compares whole tokens, through {@code ClosureClauseAccessService}, which is
     * also the component that already owns the scheme-version and effective-date gates.
     *
     * @param categoryKeys the complaint's category AND {@link AssistanceClausePrior#CATEGORY_AGNOSTIC},
     *                     or just the sentinel when the complaint has no category
     * @param entityKeys   {@code UPPER(TRIM(entity_code))} AND
     *                     {@link AssistanceClausePrior#ENTITY_AGNOSTIC}, or just the sentinel
     * @param cap          always {@code PageRequest.of(0, MAX_CANDIDATE_ROWS)}; a parameter rather than
     *                     a {@code setMaxResults} because Spring Data has no annotation for the latter
     */
    @Query("SELECT p FROM AssistanceClausePrior p "
            + "WHERE p.department = :department "
            + "AND p.categoryKey IN :categoryKeys "
            + "AND p.entityKey IN :entityKeys")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = RAIL_TIMEOUT_MS))
    List<AssistanceClausePrior> findRankedCandidates(@Param("department") String department,
                                                     @Param("categoryKeys") Collection<Long> categoryKeys,
                                                     @Param("entityKeys") Collection<String> entityKeys,
                                                     Pageable cap);

    /**
     * The four things the recommendation needs to know about the complaint being closed.
     *
     * <p>Plan: {@code ref} on {@code idx_complaint_number} (unique), one row. A constructor expression
     * rather than {@code findByComplaintNumber} because that hydrates all ~105 columns and six
     * {@code TEXT} bodies to read four scalars, on a screen the officer opens to close a complaint.
     *
     * <p>Lives in THIS interface rather than in {@code ComplaintRepository} — which has a
     * {@code findRailContext} of the same shape — because that file is a chokepoint under concurrent
     * edit and because {@code findRailContext} carries no {@code department}, which is this feature's
     * leading key column. Spring Data resolves {@code @Query} JPQL against the whole persistence unit,
     * so the declaring interface's entity is irrelevant to correctness here.
     *
     * <p>Carries no complainant name, email, subject or description. The recommendation is a count
     * about OTHER complaints and the officer is already looking at this one, so pulling identifying
     * text in would widen what a failure or a mis-scoped log could disclose for no gain (§4).
     */
    @Query("SELECT new com.hrms.cms.repository.ClauseRecommendationRepository$ClauseContext("
            + "c.complaintNumber, c.department, c.categoryId, c.entityCode, c.schemeVersion) "
            + "FROM Complaint c WHERE c.complaintNumber = :complaintNumber")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = RAIL_TIMEOUT_MS))
    Optional<ClauseContext> findClauseContext(@Param("complaintNumber") String complaintNumber);

    /**
     * One exact (cohort, clause) row, for the REFRESH's insert-or-update decision.
     *
     * <p>Plan: unique-key seek on {@code UK_ACP_COHORT_CLAUSE}, one row or none. Every key argument
     * must be a real value or the matching sentinel — never null, because a NULL in a composite key is
     * not comparable and the seek would miss the sentinel row that does exist.
     *
     * <p>No timeout hint, unlike the request-path reads, for the asymmetry documented on
     * {@link #REFRESH_TIMEOUT_MS}: borrowing the request budget here would make the refresh abandon its
     * work under exactly the contention it should tolerate.
     */
    @Query("SELECT p FROM AssistanceClausePrior p "
            + "WHERE p.department = :department AND p.categoryKey = :categoryKey "
            + "AND p.entityKey = :entityKey AND p.clauseCode = :clauseCode")
    Optional<AssistanceClausePrior> findCohortClause(@Param("department") String department,
                                                     @Param("categoryKey") Long categoryKey,
                                                     @Param("entityKey") String entityKey,
                                                     @Param("clauseCode") String clauseCode);

    /**
     * The closed, clause-bearing complaints the rollup is mined from, one keyset page at a time.
     *
     * <p>Plan: the job reads EVERY qualifying row by design, so there is nothing to seek and no index
     * is added for it (see the foot of V116). The walk is {@code WHERE c.id > :afterId ORDER BY c.id},
     * which rides the primary key. An offset-based page would re-read rows as the table grew underneath
     * a long refresh and would degrade quadratically on the deep pages.
     *
     * <p>FILTERS AT THE SOURCE, for three separate reasons that each matter:
     * <ul>
     *   <li>{@code status = 'closed'} — a clause on a non-closed complaint is a draft, not a precedent.
     *       MEASURED: 7 of the 877 clause-bearing rows are not closed, including 1 {@code pending} and 6
     *       {@code in_progress}, and counting those would let a clause someone is still deciding on vote
     *       for itself.
     *   <li>a non-blank {@code closureClause} — the denominator must be "closures that recorded a
     *       clause", not "closures". MEASURED: 2981 complaints are closed and only 870 carry a clause,
     *       so including the rest would divide every numerator by 3.4 and understate every share
     *       against a floor. The blank check is not cosmetic; the column stores {@code ''} as well as
     *       NULL elsewhere in this schema.
     *   <li>a non-blank {@code department} — it is the leading key column, so a row without one can key
     *       no cohort the read could ever reach. MEASURED: exactly 1 clause-bearing row has none.
     * </ul>
     *
     * <p>{@code entityCode} and {@code categoryId} are NOT filtered: they are the two sentinel-bearing
     * key parts, and a row missing either still counts toward its agnostic cohort. That is what makes
     * the sentinels earn their place rather than merely absorb nulls.
     */
    @Query("SELECT new com.hrms.cms.repository.ClauseRecommendationRepository$ClosedClause("
            + "c.id, c.department, c.categoryId, c.entityCode, c.closureClause) "
            + "FROM Complaint c "
            + "WHERE c.id > :afterId "
            + "AND c.status = 'closed' "
            + "AND c.closureClause IS NOT NULL AND TRIM(c.closureClause) <> '' "
            + "AND c.department IS NOT NULL AND TRIM(c.department) <> '' "
            + "ORDER BY c.id ASC")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = REFRESH_TIMEOUT_MS))
    List<ClosedClause> findClosuresForRollup(@Param("afterId") Long afterId, Pageable page);

    /**
     * Deletes (cohort, clause) rows the latest refresh did not rewrite.
     *
     * <p>Needed because the refresh UPSERTS rather than truncating-and-reloading — a truncate would
     * leave the picker unranked for the duration of every refresh, which is a self-inflicted outage on
     * a feature whose whole contract is to be there when the screen loads. The cost of upserting is
     * that a clause which has FALLEN BELOW the occurrence or share floor, or whose cohort has, would
     * otherwise keep its last computed row forever and the picker would go on recommending a clause the
     * data no longer supports. On a closure control that is the worst of the available failures.
     *
     * <p>Keyed on {@code REFRESHED_AT} rather than on a list of surviving ids: the job stamps every row
     * it writes with ONE timestamp taken at the start of the pass, so "older than this run" is exactly
     * "not rewritten by this run" — no id list to carry and no second query to build it.
     *
     * <p>CALLED ONLY AFTER A COMPLETE PASS. A requirement on the caller, not a property of this query:
     * a pass that aborted halfway has stamped only the cohorts it reached, so sweeping on its stamp
     * would delete every cohort it had not got to yet. {@code ClauseRecommendationRefreshService}
     * therefore runs the sweep after the upsert loop returns normally and skips it on failure.
     */
    // @Transactional on the repository method, not inherited from a caller: the refresh service
    // deliberately runs no transaction of its own (a self-invoked @Transactional would be inert —
    // Spring's proxy is bypassed), so a modifying query called from it would otherwise fail with
    // TransactionRequiredException. Declaring it here puts the boundary where the proxy IS the bean.
    @Transactional
    @Modifying
    @Query("DELETE FROM AssistanceClausePrior p WHERE p.refreshedAt < :staleBefore")
    int deleteStale(@Param("staleBefore") LocalDateTime staleBefore);

    /**
     * The complaint facts the recommendation keys on.
     *
     * <p>{@code schemeVersion} is carried because {@code ClosureClauseAccessService} needs it to decide
     * which clause set is in force for THIS complaint — a reprocessed complaint must be offered the
     * clauses it was originally closed under (UST769), and recommending a clause the picker will not
     * list would be worse than recommending nothing. It is passed through, never defaulted here.
     *
     * @param department  may be null or blank; the caller treats that as "no recommendation", because
     *                    it is the leading key column and nothing could be looked up without it
     * @param categoryId  nullable — the common case. The caller substitutes the sentinel
     * @param entityCode  nullable and DIRTY. The caller normalises with {@code UPPER(TRIM(...))} to
     *                    match what the refresh wrote, and substitutes the sentinel when blank
     */
    record ClauseContext(String complaintNumber,
                         String department,
                         Long categoryId,
                         String entityCode,
                         String schemeVersion) {
    }

    /**
     * One past closure reduced to the four things the rollup counts, plus its id.
     *
     * <p>Used ONLY by the scheduled refresh, never on a request. {@code id} is carried for KEYSET
     * PAGING and nothing else.
     */
    record ClosedClause(Long id,
                        String department,
                        Long categoryId,
                        String entityCode,
                        String closureClause) {
    }
}
