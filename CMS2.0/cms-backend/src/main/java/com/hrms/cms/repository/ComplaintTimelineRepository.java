package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintTimelineRepository extends JpaRepository<ComplaintTimeline, Long> {
    List<ComplaintTimeline> findByComplaintIdOrderByPerformedAtDesc(Long complaintId);
}
