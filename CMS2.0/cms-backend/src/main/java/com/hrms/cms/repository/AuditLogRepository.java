package com.hrms.cms.repository;

import com.hrms.cms.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByComplaintNumberOrderByTimestampDesc(String complaintNumber);

    List<AuditLog> findByActorOrderByTimestampDesc(String actor);

    List<AuditLog> findByActionAndTimestampBetween(String action, LocalDateTime start, LocalDateTime end);

    List<AuditLog> findByComplaintNumberAndActionOrderByTimestampDesc(String complaintNumber, String action);
}
