package com.hrms.cms.repository;

import com.hrms.cms.entity.Appeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    Optional<Appeal> findByAppealNumber(String appealNumber);

    List<Appeal> findByOriginalComplaintNumber(String originalComplaintNumber);

    List<Appeal> findByStatusNotInOrderByCreatedAtDesc(List<String> excludeStatuses);

    List<Appeal> findByAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(String assignedOfficer, List<String> excludeStatuses);

    List<Appeal> findByAssignedRoleAndStatusNotInOrderByCreatedAtDesc(String assignedRole, List<String> excludeStatuses);

    List<Appeal> findByStatus(String status);

    /**
     * Backs the AA home views: All Open Appeals, Open Representations, Assigned To Me and Created By
     * Me, each optionally combined.
     *
     * One query with nullable filters rather than a derived-name method per view: the views differ
     * only in which predicates apply, and the dashboard previously fetched everything and filtered in
     * the browser — which cannot express "open" without knowing the status vocabulary client-side.
     */
    @Query("""
            SELECT a FROM Appeal a
             WHERE (:classificationType IS NULL OR a.classificationType = :classificationType)
               AND (:assignedOfficer   IS NULL OR a.assignedOfficer   = :assignedOfficer)
               AND (:assignedRole      IS NULL OR a.assignedRole      = :assignedRole)
               AND (:createdBy         IS NULL OR a.createdBy         = :createdBy)
               AND (:status            IS NULL OR a.status            = :status)
               AND (:openOnly = FALSE OR a.status NOT IN :terminalStatuses)
             ORDER BY a.createdAt DESC
            """)
    List<Appeal> findForView(@Param("classificationType") String classificationType,
                            @Param("assignedOfficer") String assignedOfficer,
                            @Param("assignedRole") String assignedRole,
                            @Param("createdBy") String createdBy,
                            @Param("status") String status,
                            @Param("openOnly") boolean openOnly,
                            @Param("terminalStatuses") List<String> terminalStatuses);

    long countByClassificationType(String classificationType);
}
