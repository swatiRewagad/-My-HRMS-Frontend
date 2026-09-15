package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ComplaintTimelineRepository extends JpaRepository<ComplaintTimeline, Long> {
    List<ComplaintTimeline> findByComplaintIdOrderByPerformedAtDesc(Long complaintId);

    @Query("SELECT DISTINCT ct.complaintId FROM ComplaintTimeline ct WHERE ct.performedBy = :officer")
    List<Long> findDistinctComplaintIdsByPerformedBy(@Param("officer") String officer);
}
