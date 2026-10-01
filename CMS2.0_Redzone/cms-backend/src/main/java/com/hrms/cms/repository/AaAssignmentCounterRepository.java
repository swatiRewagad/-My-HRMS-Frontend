package com.hrms.cms.repository;

import com.hrms.cms.entity.AaAssignmentCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AaAssignmentCounterRepository extends JpaRepository<AaAssignmentCounter, Long> {

    Optional<AaAssignmentCounter> findByRoleGroup(String roleGroup);

    /**
     * Takes a row-level write lock on this role group's pointer for the duration of the transaction.
     *
     * This is what serialises concurrent assignment. Every assignment in a role group must pass
     * through this lock BEFORE reading the pool, so two simultaneous callers cannot both observe the
     * same officer as being one below their threshold and both assign to them.
     *
     * The predecessor acquired its lock after reading the pool, which left the workload snapshot
     * outside the critical section and made the lock decorative.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AaAssignmentCounter c WHERE c.roleGroup = :roleGroup")
    Optional<AaAssignmentCounter> findByRoleGroupForUpdate(@Param("roleGroup") String roleGroup);
}
