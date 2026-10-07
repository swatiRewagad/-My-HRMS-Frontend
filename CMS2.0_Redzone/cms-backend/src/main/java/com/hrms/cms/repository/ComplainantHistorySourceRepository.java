package com.hrms.cms.repository;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.projection.ComplainantHistoryProjections;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * The ONE read the complainant-history refresh makes over its source data.
 *
 * <h2>Its own interface, not two more methods on {@code ComplaintRepository}</h2>
 * {@code ComplaintRepository} is a chokepoint file several concurrent sessions edit, and this query is
 * read by exactly one scheduled job. Keeping it here makes the job's whole data access reviewable in one
 * place and means a change to it cannot conflict with a change to the complaint grid. It follows
 * {@code ClauseAffinitySourceRepository}, which exists for the same reason.
 *
 * <p>Extends {@link Repository} rather than {@code JpaRepository} on purpose. {@code JpaRepository} would
 * inherit {@code findAll()} and {@code count()} over the ~105-column {@code COMPLAINTS} table —
 * unbounded finders that {@code §6.2} forbids, sitting one autocomplete away from a caller who needed a
 * row cap. A marker-interface repository exposes only the capped method declared below, so the rule is
 * enforced by the TYPE rather than by review.
 *
 * <h2>THE REQUEST PATH NEVER CALLS THIS</h2>
 * It is a full scan of the register, which is exactly what {@code §6.2} means by "rollups are computed on
 * a schedule, never on request". The read side of this feature talks to
 * {@link AssistanceComplainantHistoryRepository} and to nothing else, and that interface has no finder
 * that touches {@code COMPLAINTS} at all.
 */
public interface ComplainantHistorySourceRepository extends Repository<Complaint, Long> {

    /**
     * One page of complaints, keyset-paged, with only the columns the projection keys on.
     *
     * <p>KEYSET, not offset. The caller passes the last id it saw. An {@code OFFSET} over this table is
     * wrong twice: complaints are filed during a long refresh and shift every later page (so a complaint
     * is projected twice or missed), and deep offsets degrade because the database must walk and discard
     * everything before them. {@code id > :afterId ORDER BY id} has neither problem and needs no index
     * that does not already exist — it is a range scan on the primary key.
     *
     * <h3>NO FILTER AT ALL, and that is the difference from the clause rollup</h3>
     * {@code ClauseAffinitySourceRepository} restricts its scan to the 877 rows carrying a closure clause,
     * because a complaint with no citation is no evidence about citations. This job CANNOT filter, and the
     * reason is structural: a complainant's FIRST complaint is as much a part of their filing history as
     * their tenth, so a row that looks uninteresting on its own is the denominator for the next one. In
     * particular there is no status filter and no "closed" predicate — the duplicate signal is
     * overwhelmingly about OPEN complaints, since its whole purpose is to catch double-handling before it
     * happens.
     *
     * <p>Rows with no usable contact at ALL are not filtered here either, even though they can never
     * match anything. Two reasons: the filter would have to be {@code (email IS NULL OR TRIM(...) = '')
     * AND (phone ...)}, which wraps two columns in functions for a saving of 51 rows out of 4,403; and the
     * refresh must still be able to DELETE a stale row for a complaint whose contact was removed, which it
     * does by projecting it with both sentinels rather than by leaving the old row behind. The service
     * drops the contactless rows, where the rule is visible and testable.
     *
     * <h3>Nine scalars, and no narrative</h3>
     * Neither {@code subject} nor {@code description} is selected, and there is no field for a complainant
     * NAME, ADDRESS or ACCOUNT NUMBER. That is a PRIVACY control at the earliest possible point: the job
     * cannot copy into the projection table what it never reads. See
     * {@code AssistanceComplainantHistory}'s class javadoc.
     *
     * <p>No {@code @QueryHints} timeout, unlike every read on the request path, and the asymmetry is the
     * point: a request would rather give up than stall the screen beside it, whereas this job would rather
     * wait than abandon a pass and leave the projection reporting last cycle's history. Borrowing a 5s
     * request budget here would make the refresh fail under exactly the contention it should tolerate. The
     * bound that does apply is the {@code Pageable} the caller must supply.
     *
     * @param afterId exclusive lower bound on the complaint id; pass 0 to start
     * @param page    the {@code §6.2} row cap, supplied by the caller. There is no unbounded overload, so
     *                a cap cannot be forgotten at a call site.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.ComplainantHistoryProjections$ComplainantRow("
            + "c.id, c.complaintNumber, c.complainantEmail, c.complainantPhone, c.entityCode, "
            + "c.department, c.status, c.filedAt, c.createdAt, c.maintainabilityDetermination, "
            + "c.closureCause, c.closureClause) "
            + "FROM Complaint c "
            + "WHERE c.id > :afterId "
            + "ORDER BY c.id")
    List<ComplainantHistoryProjections.ComplainantRow> findComplainantRows(
            @Param("afterId") long afterId, Pageable page);
}
