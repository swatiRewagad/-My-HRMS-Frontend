package com.hrms.cms.repository;

import com.hrms.cms.entity.ReassignmentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReassignmentHistoryRepository extends JpaRepository<ReassignmentHistory, Long> {

    List<ReassignmentHistory> findByComplaintNumberOrderByReassignedAtDesc(String complaintNumber);

    Page<ReassignmentHistory> findByEntityCodeOrderByReassignedAtDesc(String entityCode,
                                                                    Pageable pageable);

    /**
     * UST844 report, entity-scoped with an optional date window.
     *
     * <p>Nulls are handled in the query rather than by branching into several repository methods, so
     * every filter combination goes through one code path and cannot diverge in its scoping rule.
     * {@code entityCode} is intentionally NOT nullable here — an unscoped variant would be one typo
     * away from returning every entity's staffing history.
     */
    @Query("SELECT h FROM ReassignmentHistory h WHERE h.entityCode = :entityCode "
         + "AND (:fromUserId IS NULL OR h.fromUserId = :fromUserId) "
         + "AND (:toUserId IS NULL OR h.toUserId = :toUserId) "
         + "AND (:from IS NULL OR h.reassignedAt >= :from) "
         + "AND (:to IS NULL OR h.reassignedAt <= :to) "
         + "ORDER BY h.reassignedAt DESC")
    Page<ReassignmentHistory> search(@Param("entityCode") String entityCode,
                                     @Param("fromUserId") String fromUserId,
                                     @Param("toUserId") String toUserId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     Pageable pageable);

    /** Per-officer outbound totals for the report summary. */
    @Query("SELECT h.fromUserId, COUNT(h) FROM ReassignmentHistory h "
         + "WHERE h.entityCode = :entityCode AND h.fromUserId IS NOT NULL "
         + "AND (:from IS NULL OR h.reassignedAt >= :from) "
         + "AND (:to IS NULL OR h.reassignedAt <= :to) "
         + "GROUP BY h.fromUserId ORDER BY COUNT(h) DESC")
    List<Object[]> countOutboundByOfficer(@Param("entityCode") String entityCode,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    @Query("SELECT h.toUserId, COUNT(h) FROM ReassignmentHistory h "
         + "WHERE h.entityCode = :entityCode "
         + "AND (:from IS NULL OR h.reassignedAt >= :from) "
         + "AND (:to IS NULL OR h.reassignedAt <= :to) "
         + "GROUP BY h.toUserId ORDER BY COUNT(h) DESC")
    List<Object[]> countInboundByOfficer(@Param("entityCode") String entityCode,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    long countByEntityCode(String entityCode);
}
