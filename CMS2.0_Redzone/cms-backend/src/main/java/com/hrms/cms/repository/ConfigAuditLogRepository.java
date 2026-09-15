package com.hrms.cms.repository;

import com.hrms.cms.entity.ConfigAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfigAuditLogRepository extends JpaRepository<ConfigAuditLog, Long> {

    List<ConfigAuditLog> findByConfigKeyOrderByChangedAtDesc(String configKey);
}
