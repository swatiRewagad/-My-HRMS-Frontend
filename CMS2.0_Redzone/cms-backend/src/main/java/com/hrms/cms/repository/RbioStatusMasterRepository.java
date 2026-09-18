package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioStatusMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RbioStatusMasterRepository extends JpaRepository<RbioStatusMaster, String> {

    List<RbioStatusMaster> findByIsActiveOrderByDisplayOrderAsc(String isActive);

    /**
     * The legacy status strings that mean "closed". THE authoritative closed-status list, replacing the
     * hardcoded copies in WorkflowController and NotificationScheduledTasks.
     *
     * <p>DISTINCT because several status codes map to one legacy value, and a duplicated value in a
     * {@code NOT IN} clause is harmless but makes the generated SQL confusing to read in a slow-query
     * log.
     */
    @Query("""
           SELECT DISTINCT s.legacyValue FROM RbioStatusMaster s
           WHERE s.isClosed = 'Y' AND s.isActive = 'Y' AND s.legacyValue IS NOT NULL
           """)
    List<String> findClosedLegacyValues();
}
