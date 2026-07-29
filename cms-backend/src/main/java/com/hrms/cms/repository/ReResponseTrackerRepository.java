package com.hrms.cms.repository;

import com.hrms.cms.entity.ReResponseTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReResponseTrackerRepository extends JpaRepository<ReResponseTracker, Long> {

    Optional<ReResponseTracker> findByComplaintId(Long complaintId);

    List<ReResponseTracker> findByRegulatedEntityIdOrderByForwardedAtDesc(Long regulatedEntityId);

    List<ReResponseTracker> findByBreachedTrueOrderByForwardedAtDesc();

    @Query("SELECT r FROM ReResponseTracker r WHERE r.breached = false AND r.respondedAt IS NULL AND r.windowExpiresAt < :now")
    List<ReResponseTracker> findPendingBreaches(@Param("now") LocalDateTime now);

    @Query("SELECT r.regulatedEntityId, COUNT(r), " +
           "AVG(TIMESTAMPDIFF(HOUR, r.forwardedAt, COALESCE(r.respondedAt, CURRENT_TIMESTAMP))) " +
           "FROM ReResponseTracker r GROUP BY r.regulatedEntityId")
    List<Object[]> getReResponsivenessStats();

    long countByRegulatedEntityIdAndBreachedTrue(Long regulatedEntityId);

    long countByRegulatedEntityId(Long regulatedEntityId);
}
