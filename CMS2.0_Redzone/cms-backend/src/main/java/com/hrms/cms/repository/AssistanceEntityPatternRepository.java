package com.hrms.cms.repository;

import com.hrms.cms.entity.AssistanceEntityPattern;
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
 * The entity-pattern rollup: one keyed read for the rail, and the refresh job's write paths
 * (Brief 21 §5.3.5).
 *
 * <h2>The read is ONE index seek, which is the entire reason the table exists</h2>
 * §5.3.5 says the signal comes "from a scheduled rollup only... Never computed live", and §6.2 says
 * rollups are computed on a schedule and never on request. {@link #findRailCandidates} is that seek:
 * one index range on {@code UK_AEP_COHORT}'s leading columns.
 *
 * <p>NOT because {@code entity_code} is unindexed. {@code idx_complaint_entity_code} EXISTS, and the
 * live form of this question plans as {@code type=ref, rows=155} on {@code cms_db} — a seek, not a
 * scan. It is forbidden rather than slow: the rail renders on EVERY staff screen load, so an aggregate
 * here is an aggregate on the critical path of every page in the product, multiplied by however many
 * signals later get added beside it. This table turns that into a seek on tens of rows.
 *
 * <p>The REQUEST path therefore never aggregates anything. No finder here touches {@code COMPLAINTS}.
 *
 * <h2>DEPARTMENT IS PASSED BY THE CALLER AND IS NOT OPTIONAL</h2>
 * Every read below takes a department and ANDs it in. There is deliberately no finder that omits it and
 * no finder that takes a COLLECTION of them, and that absence is the control — §5.3.5 is a
 * cross-complaint disclosure (it reports OTHER complainants' live cases against a named entity), so
 * Brief 21 §4's restrictive default applies until a human rules otherwise. The schema holds no
 * cross-department row to serve and this interface exposes no way to ask for one. Adding either is a
 * disclosure change requiring the ruling recorded in the findings as open ask 3.
 *
 * <p>{@code EmailSyndicationApiController:451} is the recorded precedent for why this matters: it
 * returned every row in the system when its owner parameter was omitted, because the scope was an
 * optional input. A nullable department here would be the same defect with a different column.
 *
 * <h2>The rail reads CANDIDATES and chooses in Java; the refresh reads ONE row</h2>
 * {@link #findCohort} is the refresh's upsert seek: it knows the exact window it just computed, so it
 * asks for exactly that row. {@link #findRailCandidates} cannot, because TWO of the five key columns are
 * SENTINELLED — {@link AssistanceEntityPattern#GROUND_AGNOSTIC} and
 * {@link AssistanceEntityPattern#SCOPE_AGNOSTIC} — so the specific row and its fallback are both
 * legitimate answers. Both values of each go into one {@code IN} list, which is a single index range
 * rather than four seeks, and the caller picks between what comes back.
 *
 * <p>The PREFERENCE between the returned rows is deliberately NOT an {@code ORDER BY}. It is three
 * rules — ground-specific over agnostic, then office-specific over agnostic, then the larger
 * denominator — and {@code AssistanceRailService} applies them where a reader can see them and a unit
 * test can pin them, rather than leaving an officer's signal to be decided by a clause in a string.
 * Same reasoning as {@code AssistanceNextActionRepository}.
 *
 * <h2>Why a row cap on a read that can return at most four rows</h2>
 * {@link #findRailCandidates} takes a {@link Pageable} even though {@code UK_AEP_COHORT} bounds the
 * result at two grounds x two offices. §6.2 requires every query to carry a cap regardless, and the
 * reason is worth stating: the cap protects against the SCHEMA being wrong, not against the data being
 * large. A unique constraint that failed to apply — an Oracle migration run partially, or a table
 * created by Hibernate's {@code ddl-auto} instead of by the migration — would silently permit
 * duplicates, and an uncapped read would then hydrate however many exist onto a request path. The cap
 * makes that a bounded degradation instead of an unbounded one.
 */
public interface AssistanceEntityPatternRepository
        extends JpaRepository<AssistanceEntityPattern, Long> {

    /** Shared with {@code ComplaintRepository}; spelled out because interfaces cannot inherit it. */
    String QUERY_TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    /**
     * The rail read budget, matching the dashboard aggregates' and the next-action rollup's 5s.
     *
     * <p>The same number for a much smaller query, on purpose: the value is a CEILING past which the
     * caller would rather have no answer, not an expectation of how long the query takes. A tighter
     * bound here would make the signal flap under incidental contention, and a flapping ambient signal
     * is worse than an absent one — an officer who sees it sometimes cannot tell whether its absence
     * means "no pattern" or "the query was slow".
     */
    String RAIL_TIMEOUT_MS = "5000";

    /**
     * Hard row cap on the request-path read.
     *
     * <p>Sixteen for a query whose key space is four rows, so the cap is slack rather than a throttle.
     * See the interface javadoc for why it exists anyway.
     */
    int MAX_CANDIDATE_ROWS = 16;

    /**
     * Every window that could apply to this complaint, within this department.
     *
     * <p>Plan: index range on {@code UK_AEP_COHORT}'s leading {@code ENTITY_KEY}, with
     * {@code DEPARTMENT} and {@code QUARTER_KEY} as equalities and the two small {@code IN} lists
     * filtered from the same index. At most four rows, because the unique key cannot hold more than one
     * row per (entity, ground, quarter, department, office).
     *
     * <p>NO FUNCTION WRAPS THE INDEXED COLUMNS, and that is the point of normalising on the write side.
     * {@code UPPER(TRIM(e.entityKey))} here would defeat the index — a recorded offence elsewhere in
     * this codebase — so the caller alias-resolves and upper-cases through
     * {@code AssistanceEntityAliasNormaliser} BEFORE calling, comparing a normalised parameter against
     * an already-normalised column. The same is true of {@code department}.
     *
     * @param entityKey   the complaint's {@code entity_code}, ALREADY normalised by the caller through
     *                    {@code AssistanceEntityAliasNormaliser}. A raw value silently matches nothing on
     *                    Oracle (case-sensitive collation) and matches the wrong window or none on
     *                    MySQL, which is why normalisation is the caller's precondition and not this
     *                    query's job.
     * @param department  the tenancy fence, upper-cased, never null — see the interface javadoc.
     * @param quarterKey  {@code yyyyQ} for the quarter the complaint was FILED in
     * @param groundKeys  the complaint's ground AND {@link AssistanceEntityPattern#GROUND_AGNOSTIC}, or
     *                    just the sentinel when it has none — which today is always, the column being
     *                    populated on 0 of 4,403 rows. Both together so the fallback costs no second
     *                    round trip.
     * @param officeCodes the complaint's office AND {@link AssistanceEntityPattern#SCOPE_AGNOSTIC}, or
     *                    just the sentinel. Same argument; {@code rbio_office_code} is populated on 410
     *                    of 4,403.
     * @param page        the §6.2 row cap, always {@code PageRequest.of(0, MAX_CANDIDATE_ROWS)} — a
     *                    parameter rather than {@code setMaxResults} because Spring Data has no
     *                    annotation for the latter
     */
    @Query("SELECT e FROM AssistanceEntityPattern e "
            + "WHERE e.entityKey = :entityKey AND e.department = :department "
            + "AND e.quarterKey = :quarterKey "
            + "AND e.groundKey IN :groundKeys AND e.officeCode IN :officeCodes")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = RAIL_TIMEOUT_MS))
    List<AssistanceEntityPattern> findRailCandidates(@Param("entityKey") String entityKey,
                                                     @Param("department") String department,
                                                     @Param("quarterKey") Integer quarterKey,
                                                     @Param("groundKeys") Collection<Long> groundKeys,
                                                     @Param("officeCodes") Collection<String> officeCodes,
                                                     Pageable page);

    /**
     * One exact window, for the REFRESH's insert-or-update decision.
     *
     * <p>Plan: unique-key seek on {@code UK_AEP_COHORT}, one row or none. Every key component must be
     * non-null — a real id or {@link AssistanceEntityPattern#GROUND_AGNOSTIC}, a real code or
     * {@link AssistanceEntityPattern#SCOPE_AGNOSTIC} — because a NULL in a composite key is not
     * comparable and the seek would MISS the sentinel row that does exist, so every refresh would insert
     * a second one and the constraint would reject it.
     *
     * <p>NO TIMEOUT HINT, unlike {@link #findRailCandidates}, and the asymmetry is deliberate: a request
     * would rather give up than stall the screen beside it, whereas the job would rather wait than skip
     * a window and leave the rollup reporting last cycle's number. Borrowing the request budget here
     * would make the refresh abandon its work under exactly the contention it should tolerate.
     */
    @Query("SELECT e FROM AssistanceEntityPattern e "
            + "WHERE e.entityKey = :entityKey AND e.groundKey = :groundKey "
            + "AND e.quarterKey = :quarterKey AND e.department = :department "
            + "AND e.officeCode = :officeCode")
    Optional<AssistanceEntityPattern> findCohort(@Param("entityKey") String entityKey,
                                                 @Param("groundKey") Long groundKey,
                                                 @Param("quarterKey") Integer quarterKey,
                                                 @Param("department") String department,
                                                 @Param("officeCode") String officeCode);

    /**
     * Deletes windows the latest refresh did not rewrite.
     *
     * <p>Needed because the refresh UPSERTS rather than truncating-and-reloading — a truncate would
     * leave the rail silent for the duration of every refresh, a self-inflicted outage on a feature
     * whose whole contract is to be there when the screen loads. The cost of upserting is that a window
     * which has FALLEN BELOW the floor would otherwise keep its last computed row forever, and the rail
     * would go on reporting open cases that are no longer open. That is worse here than on the
     * next-action rollup: a stale "14 open cases" about a named entity is a false statement about a
     * regulated institution, not merely an unhelpful suggestion.
     *
     * <p>It also bounds the table. {@code QUARTER_KEY} is part of the key, so rows ACCUMULATE per
     * quarter rather than being overwritten in place; without a sweep the table would grow without
     * limit and keep serving quarters nobody asked about. That makes the sweep load-bearing here in a
     * way it is not on a rollup whose key space is fixed.
     *
     * <p>Keyed on {@code REFRESHED_AT} rather than on a list of surviving ids: the job stamps every row
     * it writes with ONE timestamp taken at the start of the pass, so "older than this run" is exactly
     * "not rewritten by this run" — no id list to carry and no second query to build it.
     *
     * <p>CALLED ONLY AFTER A COMPLETE PASS. That is a requirement on the CALLER, not a property of this
     * query: a pass that aborted halfway has stamped only the windows it reached, so sweeping on its
     * stamp would delete every window it had not got to yet and the rail would go quiet on them until
     * the next successful run. {@code AssistanceEntityPatternRefreshService} therefore runs the sweep
     * after the upsert loop returns normally, and skips it on failure.
     */
    // @Transactional on the repository method, not inherited from a caller: the refresh service
    // deliberately runs no transaction of its own (a self-invoked @Transactional would be INERT — it
    // does not pass through the Spring proxy), so a modifying query called from it would otherwise fail
    // with TransactionRequiredException. Declaring it here means the boundary exists wherever this is
    // called from. Same shape and same reason as AssistanceNextActionRepository#deleteStale.
    @Transactional
    @Modifying
    @Query("DELETE FROM AssistanceEntityPattern e WHERE e.refreshedAt < :staleBefore")
    int deleteStale(@Param("staleBefore") LocalDateTime staleBefore);
}
