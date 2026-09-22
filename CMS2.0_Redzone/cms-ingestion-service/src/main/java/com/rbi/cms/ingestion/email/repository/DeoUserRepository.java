package com.rbi.cms.ingestion.email.repository;

import com.rbi.cms.ingestion.email.entity.DeoUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeoUserRepository extends JpaRepository<DeoUser, Long> {

    Optional<DeoUser> findByUserId(String userId);

    @Query("SELECT d FROM DeoUser d WHERE d.isActive = true AND d.isOnLeave = false AND d.currentAssignedCount < d.maxThreshold ORDER BY d.currentAssignedCount ASC, d.sortOrder ASC")
    List<DeoUser> findEligibleDeos();

    List<DeoUser> findByIsActiveTrueOrderBySortOrderAsc();

    long countByIsActiveTrueAndIsOnLeaveFalse();
}
