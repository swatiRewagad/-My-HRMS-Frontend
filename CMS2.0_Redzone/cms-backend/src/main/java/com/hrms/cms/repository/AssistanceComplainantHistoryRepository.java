package com.hrms.cms.repository;

import com.hrms.cms.entity.AssistanceComplainantHistory;
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
 * The complainant filing-history projection: two keyed reads for the request path, and the job's writes.
 *
 * <h2>The read is TWO INDEX SEEKS, and neither is an aggregate</h2>
 * {@code §5.3}'s governing rule is that every signal must be "answerable by a single keyed lookup" and
 * {@code §6.2} forbids computing a rollup on request. This feature needs TWO lookups rather than one
 * because the identity rule is "email OR phone", and an OR across two columns cannot be index-sought as
 * one predicate on either engine. That is not a guess — it is the MEASURED reason
 * {@code ComplaintRepository.countOtherComplaintsByComplainantEmail} declined to match phone at all:
 * 1 row examined with email alone against 2,509 with the OR. Two seeks recover the recall the existing
 * signal gave up, without the scan.
 *
 * <p>{@link #findByEmailKey} and {@link #findByPhoneKey} are each a leading-equality seek on
 * {@code IDX_ACH_EMAIL} / {@code IDX_ACH_PHONE} with {@code FILED_AT} as the trailing sort column, so the
 * {@code ORDER BY ... DESC} is served by the index with no filesort. They return ROWS, not counts: the
 * duplicate signal must NAME the earlier complaints so the officer can open them, and the service then
 * applies the window, the entity match and the SCOPE filter over at most
 * {@link AssistanceComplainantHistory#MAX_HISTORY_ROWS} rows in Java.
 *
 * <p>So there is no {@code GROUP BY}, no {@code COUNT(*)}, no {@code DISTINCT} and no window function on
 * the request path. The lifetime count the repeat-complainant signal reports is the SIZE of a bounded
 * result the seek already returned — the same arithmetic {@code ClosureClauseRecommendationService} does
 * over the 64 rows of a cohort. The measured bound is tight: the largest history behind one email in this
 * register is 10 complaints, and behind one phone surviving the fan-out guard, 19.
 *
 * <h2>Why neither read filters by department in SQL</h2>
 * The SCOPE filter is applied in the service, not in these queries, and that is deliberate. Adding
 * {@code AND h.department IN :departments} would put the tenancy predicate between the key and
 * {@code FILED_AT}, costing the index its range scan — but more importantly it would make the DENOMINATOR
 * unobservable. The service needs to know that rows were REMOVED by scope in order to report an honest
 * total and to log a suppression, and a query that never returned them cannot tell it. Filtering in Java
 * over a bounded set keeps the rule in one place, where the per-role negative tests can pin it.
 *
 * <p>The security consequence is nil, because the scope filter is NOT optional in the service: there is
 * one method that applies it and no path around it. See {@code DuplicateFilingDetectionService}.
 *
 * <h2>Every query declares a cap AND a timeout</h2>
 * Per {@code §6.2}. The cap is a {@code Pageable} the caller must supply — there is no unbounded finder
 * declared here — and the timeout is the 5s ceiling the rest of the assistance set uses. The ceiling is
 * not a latency target; it is the point past which the caller would rather show an officer no duplicate
 * signal than hold up the complaint screen.
 *
 * <p>{@code JpaRepository} still inherits {@code findAll()} and {@code count()}, which is unavoidable
 * without hand-rolling the interface. The control is that the reader calls neither, exactly as
 * {@code AssistanceJobLockRepository} documents for its own inherited {@code save}.
 */
public interface AssistanceComplainantHistoryRepository
        extends JpaRepository<AssistanceComplainantHistory, Long> {

    /** Shared with the other assistance repositories; spelled out because interfaces cannot inherit it. */
    String QUERY_TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    /**
     * The request-path read budget, matching the rest of the assistance set.
     *
     * <p>A CEILING past which the caller would rather have no signal, not an expectation. A tighter bound
     * would make the duplicate panel flicker between present and absent under incidental contention,
     * which is worse than being consistently quiet: an officer who sees a duplicate warning on one load
     * and not the next cannot tell which load was telling the truth.
     */
    String READ_TIMEOUT_MS = "5000";

    /**
     * One complainant's filing history by EMAIL, newest first.
     *
     * <p>Plan: index range on {@code IDX_ACH_EMAIL}, leading equality on {@code EMAIL_KEY}, rows returned
     * in {@code FILED_AT} order from the index itself.
     *
     * <p>The caller MUST pass an already-normalised key — lower-cased and trimmed — and must NOT pass
     * {@link AssistanceComplainantHistory#KEY_ABSENT}. Two separate reasons, both load-bearing.
     * Normalised, because a {@code LOWER(column)} wrap would defeat the index ({@code §6.2}) and because
     * MySQL folds case for free while Oracle does not, so a verbatim comparison would match in dev and
     * miss in production. Not the sentinel, because {@code '*'} is shared by every contactless complaint
     * in the register — seeking it would return a crowd of unrelated strangers as one person's history,
     * which is the single worst outcome this feature could produce. {@code DuplicateFilingDetectionService}
     * refuses the sentinel before calling, so the guard exists on both sides.
     *
     * @param page the {@code §6.2} row cap. Callers pass
     *             {@link AssistanceComplainantHistory#MAX_HISTORY_ROWS}; there is no unbounded overload.
     */
    @Query("SELECT h FROM AssistanceComplainantHistory h "
            + "WHERE h.emailKey = :emailKey "
            + "ORDER BY h.filedAt DESC")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = READ_TIMEOUT_MS))
    List<AssistanceComplainantHistory> findByEmailKey(@Param("emailKey") String emailKey, Pageable page);

    /**
     * One complainant's filing history by PHONE, newest first.
     *
     * <p>Plan: index range on {@code IDX_ACH_PHONE}, identical shape to {@link #findByEmailKey}.
     *
     * <p>The same two preconditions apply, and the sentinel one is sharper here. {@code PHONE_KEY} holds
     * {@code '*'} not only for the 49 complaints with no phone but for every complaint whose phone the
     * FAN-OUT GUARD suppressed as a placeholder — 3,699 rows, because {@code 9876543210} alone appears on
     * 3,527 complaints across 3,427 distinct emails. Seeking the sentinel here would therefore return
     * most of the register as a single person's filing history and report it as grounds for a
     * {@code 16(2)(b)} vexatious determination. The reader refuses it; this is the note that says why it
     * must never stop doing so.
     *
     * @param page the {@code §6.2} row cap, supplied by the caller
     */
    @Query("SELECT h FROM AssistanceComplainantHistory h "
            + "WHERE h.phoneKey = :phoneKey "
            + "ORDER BY h.filedAt DESC")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = READ_TIMEOUT_MS))
    List<AssistanceComplainantHistory> findByPhoneKey(@Param("phoneKey") String phoneKey, Pageable page);

    /**
     * One exact row, for the REFRESH's insert-or-update decision.
     *
     * <p>Plan: unique-key seek on {@code UK_ACH_COMPLAINT}, one row or none.
     *
     * <p>No timeout hint, unlike the two reads above, and the asymmetry is the point: a request would
     * rather give up than stall the screen beside it, whereas the job would rather wait than skip a
     * complaint and leave the projection missing a row that is some other complainant's denominator.
     */
    @Query("SELECT h FROM AssistanceComplainantHistory h WHERE h.complaintNumber = :complaintNumber")
    Optional<AssistanceComplainantHistory> findByComplaintNumber(
            @Param("complaintNumber") String complaintNumber);

    /**
     * Deletes rows the latest refresh did not rewrite.
     *
     * <p>Needed because the refresh UPSERTS rather than truncating-and-reloading — a truncate would leave
     * every complaint screen with no duplicate signal for the duration of every refresh, a self-inflicted
     * degradation on a feature whose contract is to be there when the screen opens. The cost of upserting
     * is that a row for a DELETED complaint, or for one whose contact was removed, would otherwise keep
     * its last computed form forever and go on being counted in somebody's lifetime total.
     *
     * <p>Keyed on {@code REFRESHED_AT} rather than on a list of surviving ids: the job stamps every row it
     * writes with ONE timestamp taken at the start of the pass, so "older than this run" is exactly "not
     * rewritten by this run" — no id list to carry and no second query to build it.
     *
     * <p>CALLED ONLY AFTER A COMPLETE PASS. That is a requirement on the caller, not a property of this
     * query: a pass that aborted halfway has stamped only the complaints it reached, so sweeping on its
     * stamp would delete the entire remainder of the register's history and every lifetime count would
     * silently drop. {@code AssistanceComplainantHistoryRefreshService} therefore runs the sweep after the
     * upsert loop returns normally and skips it on failure, which is enforced by control flow and by
     * nothing else — there is no transaction to roll the upserts back.
     */
    // @Transactional on the REPOSITORY method, not inherited from a caller. The refresh service
    // deliberately runs no transaction of its own — its recompute() is invoked through `this` from
    // refresh(), and Spring's proxy does not intercept a self-invocation, so a @Transactional there would
    // be INERT while reading as a boundary. Declared here, where the proxy IS the bean, so the boundary
    // exists wherever this is called from. Same resolution as AssistanceClauseAffinityRepository.
    @Transactional
    @Modifying
    @Query("DELETE FROM AssistanceComplainantHistory h WHERE h.refreshedAt < :staleBefore")
    int deleteStale(@Param("staleBefore") LocalDateTime staleBefore);
}
