package com.hrms.cms.repository;

import com.hrms.cms.entity.AaAssignmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Workload, holder and staleness queries over AA_ASSIGNMENT_RECORD.
 *
 * Every count here is computed from live rows. WF_OFFICER_POOL.CURRENT_WORKLOAD exists and looks like
 * the obvious source, but it is maintained by cms-workflow-service which decrements it using a role
 * name where a user id is expected, so it drifts from reality and cannot be used to enforce a
 * threshold.
 */
public interface AaWorkloadRepository extends JpaRepository<AaAssignmentRecord, Long> {

    /** The current holder of this appeal, if anyone. */
    Optional<AaAssignmentRecord> findByAppealNumberAndReleasedAtIsNull(String appealNumber);

    List<AaAssignmentRecord> findByAppealNumberOrderByAssignedAtDesc(String appealNumber);

    /** Records this officer holds that count against their threshold. */
    @Query("""
            SELECT COUNT(r) FROM AaAssignmentRecord r
             WHERE r.assignedUserId = :userId
               AND r.releasedAt IS NULL
               AND r.thresholdExempt = FALSE
            """)
    long countChargeableFor(@Param("userId") String userId);

    /**
     * Chargeable counts for many officers in one query, so a pool of N costs one round trip.
     *
     * Officers with no held records are absent from the result rather than present with 0 — callers
     * must default a missing entry to zero.
     */
    @Query("""
            SELECT r.assignedUserId, COUNT(r) FROM AaAssignmentRecord r
             WHERE r.assignedUserId IN :userIds
               AND r.releasedAt IS NULL
               AND r.thresholdExempt = FALSE
             GROUP BY r.assignedUserId
            """)
    List<Object[]> countChargeableGrouped(@Param("userIds") List<String> userIds);

    /** Everything this officer currently holds, exempt or not. Used before deactivating them. */
    List<AaAssignmentRecord> findByAssignedUserIdAndReleasedAtIsNullOrderByAssignedAtAsc(String userId);

    long countByAssignedUserIdAndReleasedAtIsNull(String userId);

    /**
     * Held records assigned before {@code cutoff} that the assignee has never opened.
     *
     * Backs the unclaimed-draft escalation sweep. escalatedAt must be null so a record is escalated
     * once rather than on every sweep.
     */
    @Query("""
            SELECT r FROM AaAssignmentRecord r
             WHERE r.releasedAt IS NULL
               AND r.claimedAt IS NULL
               AND r.escalatedAt IS NULL
               AND r.assignedAt < :cutoff
             ORDER BY r.assignedAt ASC
            """)
    List<AaAssignmentRecord> findUnclaimedBefore(@Param("cutoff") LocalDateTime cutoff);

    /** Held records for a role group, ordered so a rebalance processes the newest arrivals first. */
    @Query("""
            SELECT r FROM AaAssignmentRecord r
             WHERE r.roleGroup = :roleGroup
               AND r.releasedAt IS NULL
               AND r.thresholdExempt = FALSE
             ORDER BY r.assignedAt DESC
            """)
    List<AaAssignmentRecord> findHeldInRoleGroup(@Param("roleGroup") String roleGroup);

    void deleteByAppealNumberIn(List<String> appealNumbers);
}
