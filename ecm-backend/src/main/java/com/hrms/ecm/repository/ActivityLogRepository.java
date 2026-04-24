package com.hrms.ecm.repository;

import com.hrms.ecm.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findTop15ByOrderByPerformedAtDesc();
}
