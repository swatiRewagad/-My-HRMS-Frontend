package com.hrms.cms.repository;

import com.hrms.cms.entity.NodalOfficerRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NodalOfficerRecordRepository extends JpaRepository<NodalOfficerRecord, Long> {

    List<NodalOfficerRecord> findByComplaintNumber(String complaintNumber);

    List<NodalOfficerRecord> findByStatus(String status);

    List<NodalOfficerRecord> findByStatusAndLastModifiedAtBefore(String status, LocalDateTime cutoff);

    List<NodalOfficerRecord> findByEntityName(String entityName);

    List<NodalOfficerRecord> findByAssignedTo(String assignedTo);

    // ═══════════════════════════════════════════════════════════════
    // UST838 workload
    //
    // The workload figure shown in the reassignment popup and the one on the PNO dashboard must
    // agree, because a PNO reads them side by side and any discrepancy makes both untrustworthy.
    // The two queries below therefore share a byte-identical WHERE clause and differ only in
    // whether they return one officer's count or every officer's. Both are driven from
    // WorkloadService, which is the only caller.
    // ═══════════════════════════════════════════════════════════════

    /**
     * Count of records assigned to one officer, excluding the statuses the caller passes as
     * excluded (drafts and closed, per UST838).
     *
     * <p>The excluded set is a parameter rather than a literal because the four CLOSED_STATUSES
     * lists already in this codebase disagree with each other; hardcoding a fifth would guarantee
     * the workload number drifts from whatever the rest of the system considers closed.
     */
    @Query("SELECT COUNT(r) FROM NodalOfficerRecord r "
         + "WHERE r.entityCode = :entityCode AND r.assignedTo = :assignedTo "
         + "AND UPPER(r.status) NOT IN :excludedStatuses")
    long countActiveForOfficer(@Param("entityCode") String entityCode,
                               @Param("assignedTo") String assignedTo,
                               @Param("excludedStatuses") Collection<String> excludedStatuses);

    /**
     * Same predicate as {@link #countActiveForOfficer}, grouped per officer, so the dashboard needs
     * one round trip rather than one per candidate.
     */
    @Query("SELECT r.assignedTo, COUNT(r) FROM NodalOfficerRecord r "
         + "WHERE r.entityCode = :entityCode AND r.assignedTo IS NOT NULL "
         + "AND UPPER(r.status) NOT IN :excludedStatuses "
         + "GROUP BY r.assignedTo")
    List<Object[]> countActiveGroupedByOfficer(@Param("entityCode") String entityCode,
                                               @Param("excludedStatuses") Collection<String> excludedStatuses);

    Page<NodalOfficerRecord> findByEntityCodeOrderByLastModifiedAtDesc(String entityCode,
                                                                     Pageable pageable);

    Page<NodalOfficerRecord> findByEntityCodeAndAssignedToOrderByLastModifiedAtDesc(String entityCode,
                                                                                   String assignedTo,
                                                                                   Pageable pageable);

    List<NodalOfficerRecord> findByEntityCodeAndAssignedTo(String entityCode, String assignedTo);
}
