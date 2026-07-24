package com.hrms.cms.repository;

import com.hrms.cms.entity.AppealTimeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppealTimelineRepository extends JpaRepository<AppealTimeline, Long> {

    List<AppealTimeline> findByAppealNumberOrderByPerformedAtDesc(String appealNumber);
}
