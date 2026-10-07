package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.repository.projection.AssistanceRailProjections;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ComplaintTimelineRepository extends JpaRepository<ComplaintTimeline, Long> {
    List<ComplaintTimeline> findByComplaintIdOrderByPerformedAtDesc(Long complaintId);

    /**
     * History oldest-first, for the History tab (UST594-595).
     *
     * <p>Ascending, unlike every existing read path. UST594 asks for strict chronological order, and
     * UST595 requires post-reopen entries to read as APPENDED rather than merged into the original
     * handling — which is only legible reading forwards.
     *
     * <p>Secondary sort on id because several rows are written inside one transaction and can share a
     * {@code performedAt}; without it, same-instant entries would return in whatever order the database
     * chose and "strict chronological order" would not be testable.
     */
    List<ComplaintTimeline> findByComplaintIdOrderByPerformedAtAscIdAsc(Long complaintId);

    @Query("SELECT DISTINCT ct.complaintId FROM ComplaintTimeline ct WHERE ct.performedBy = :officer")
    List<Long> findDistinctComplaintIdsByPerformedBy(@Param("officer") String officer);

    /**
     * One page of countable timeline events, for the next-action rollup refresh (Brief 21 §5.3.1).
     *
     * <h3>KEYSET paged, not offset paged</h3>
     * The caller passes the last id it saw. An {@code OFFSET} over an append-only log is wrong twice:
     * rows inserted during a long refresh shift every later page (so an event is counted twice or
     * missed), and deep offsets degrade because the database must walk and discard everything before
     * them. {@code id > :afterId ORDER BY id} has neither problem, and needs no index that does not
     * already exist — it is a range scan on the primary key.
     *
     * <h3>The four WHERE clauses are all load-bearing</h3>
     * A row missing {@code fromStatus}, {@code performedByRole} or {@code action} cannot belong to a
     * cohort, because the first two ARE the key and the third is what is being counted. The
     * {@code TRIM(...) <> ''} halves are not redundant with the null checks: this database stores
     * {@code ''} as well as NULL in these columns, and an empty-string cohort would pool unrelated
     * events under one meaningless key — the same defect {@code countOtherComplaintsByComplainantEmail}
     * guards against for blank emails.
     *
     * <p>MEASURED consequence of the role filter: {@code performed_by_role} is populated on 7,531 of
     * 17,433 rows (43%), so 57% of recorded history is invisible to this rollup. The column was added
     * after the table and historical rows have no recoverable answer — it is NOT backfillable from
     * {@code performedBy}, which holds free text and spells the system actor "SYSTEM", "System" and
     * "system" interchangeably. The gap is reported rather than papered over.
     *
     * <h3>Why the category comes from an unrelated-entity LEFT JOIN</h3>
     * {@code ComplaintTimeline} holds a bare {@code complaintId} with no mapped association — the
     * entity is deliberately free of one so a timeline row survives its complaint being purged. So the
     * join is spelled explicitly with {@code ON}, which Hibernate 6 supports for unrelated entities.
     *
     * <p>LEFT and not INNER, which is the difference between a working rollup and an empty one. An
     * inner join would drop every event whose complaint is missing, and more importantly the refresh
     * needs these rows regardless of whether a category exists: a null category still counts toward the
     * category-AGNOSTIC cohort, which is where essentially all of the rollup's coverage lives today.
     *
     * @param afterId exclusive lower bound on the timeline id; pass 0 to start
     */
    @Query("SELECT new com.hrms.cms.repository.projection.AssistanceRailProjections$TimelineAction("
            + "t.id, t.fromStatus, t.performedByRole, t.action, c.categoryId) "
            + "FROM ComplaintTimeline t LEFT JOIN Complaint c ON c.id = t.complaintId "
            + "WHERE t.id > :afterId "
            + "AND t.fromStatus IS NOT NULL AND TRIM(t.fromStatus) <> '' "
            + "AND t.performedByRole IS NOT NULL AND TRIM(t.performedByRole) <> '' "
            + "AND t.action IS NOT NULL AND TRIM(t.action) <> '' "
            + "ORDER BY t.id")
    List<AssistanceRailProjections.TimelineAction> findActionsForRollup(
            @Param("afterId") Long afterId, Pageable pageable);
}
