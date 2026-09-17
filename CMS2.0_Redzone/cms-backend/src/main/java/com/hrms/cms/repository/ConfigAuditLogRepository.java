package com.hrms.cms.repository;

import com.hrms.cms.entity.ConfigAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfigAuditLogRepository extends JpaRepository<ConfigAuditLog, Long> {

    /**
     * Newest first, tie-broken by id.
     *
     * `changed_at` is second-precision, so several edits to the same key within one second sorted
     * arbitrarily and "the latest audit entry" could return a superseded row — misleading in an audit
     * trail, whose whole purpose is establishing what happened last.
     */
    List<ConfigAuditLog> findByConfigKeyOrderByChangedAtDescIdDesc(String configKey);
}
