package com.hrms.cms.repository;

import com.hrms.cms.entity.AaOfficerPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AaOfficerPoolRepository extends JpaRepository<AaOfficerPool, Long> {

    Optional<AaOfficerPool> findByUserIdAndRoleGroup(String userId, String roleGroup);

    Optional<AaOfficerPool> findByUserId(String userId);

    List<AaOfficerPool> findByRoleGroupOrderByUserIdAsc(String roleGroup);

    /**
     * Officers who may receive work right now: active and not on leave.
     *
     * There is deliberately no "active only" variant. The predecessor service fell back to one when
     * this returned empty, which re-admitted officers who were on leave — the fallback looked like
     * resilience but silently violated the eligibility rule.
     *
     * Ordered by user id so the candidate list has a stable, reproducible base order before the
     * workload sort is applied. Without it, ties would be broken by whatever order the database
     * happened to return.
     */
    @Query("""
            SELECT p FROM AaOfficerPool p
             WHERE p.roleGroup = :roleGroup
               AND p.active = TRUE
               AND p.onLeave = FALSE
             ORDER BY p.userId ASC
            """)
    List<AaOfficerPool> findEligible(@Param("roleGroup") String roleGroup);

    @Query("""
            SELECT p FROM AaOfficerPool p
             WHERE p.roleGroup = :roleGroup
               AND p.active = TRUE
               AND p.onLeave = FALSE
               AND (:regionalOffice IS NULL OR p.regionalOffice = :regionalOffice)
             ORDER BY p.userId ASC
            """)
    List<AaOfficerPool> findEligibleInRegion(@Param("roleGroup") String roleGroup,
                                             @Param("regionalOffice") String regionalOffice);

    List<AaOfficerPool> findByIdIn(List<Long> ids);
}
