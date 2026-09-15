package com.rbi.cms.workflow.repository;

import com.rbi.cms.workflow.entity.AssignmentCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssignmentCounterRepository extends JpaRepository<AssignmentCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AssignmentCounter c WHERE c.roleGroup = :roleGroup")
    Optional<AssignmentCounter> findByRoleGroupForUpdate(String roleGroup);

    Optional<AssignmentCounter> findByRoleGroup(String roleGroup);
}
