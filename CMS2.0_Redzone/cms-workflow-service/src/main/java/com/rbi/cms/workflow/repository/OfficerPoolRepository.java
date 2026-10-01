package com.rbi.cms.workflow.repository;

import com.rbi.cms.workflow.entity.OfficerPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OfficerPoolRepository extends JpaRepository<OfficerPool, Long> {

    List<OfficerPool> findByRoleGroupAndActiveTrue(String roleGroup);

    List<OfficerPool> findByRoleGroupAndActiveTrueAndOnLeaveFalse(String roleGroup);

    List<OfficerPool> findByRoleGroupAndActiveTrueAndOnLeaveFalseAndRegionalOffice(
            String roleGroup, String regionalOffice);

    @Modifying
    @Query("UPDATE OfficerPool o SET o.currentWorkload = o.currentWorkload + 1 WHERE o.userId = :userId")
    void incrementWorkload(String userId);

    @Modifying
    @Query("UPDATE OfficerPool o SET o.currentWorkload = o.currentWorkload - 1 WHERE o.userId = :userId AND o.currentWorkload > 0")
    void decrementWorkload(String userId);
}
