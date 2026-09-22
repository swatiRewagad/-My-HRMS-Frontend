package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintTimeline;
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
}
