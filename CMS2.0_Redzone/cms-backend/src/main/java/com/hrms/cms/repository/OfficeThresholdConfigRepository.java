package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficeThresholdConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfficeThresholdConfigRepository extends JpaRepository<OfficeThresholdConfig, Long> {
    Optional<OfficeThresholdConfig> findByOfficeId(String officeId);
    List<OfficeThresholdConfig> findByActiveTrueOrderByOverflowSequenceOrderAsc();
    List<OfficeThresholdConfig> findByDepartmentAndActiveTrueOrderByOverflowSequenceOrderAsc(String department);
    long countByCurrentCountGreaterThanEqualAndActiveTrue(int threshold);

    /**
     * Claims one unit of capacity at an office, atomically, and only if capacity remains.
     *
     * <p>This replaces the read-modify-write the service used to do in Java
     * ({@code setCurrentCount(getCurrentCount() + 1)}). That pattern admitted two complaints past a
     * full office: both callers read 499 against a max of 500, both evaluated the gate as true, and
     * both wrote 500. The lost update also under-counted the office permanently, because nothing
     * ever recomputes CURRENT_COUNT from live complaints.
     *
     * <p>The capacity test lives in the WHERE clause, so the database decides the winner under a
     * single row lock. A return of 0 means "no capacity" and is the caller's signal to overflow —
     * it is a normal outcome, not an error.
     *
     * @return 1 if capacity was claimed, 0 if the office was already at or above its threshold
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OfficeThresholdConfig o SET o.currentCount = o.currentCount + 1 "
         + "WHERE o.officeId = :officeId AND o.active = true AND o.currentCount < o.maxThreshold")
    int claimCapacity(@Param("officeId") String officeId);

    /**
     * Increments the counter WITHOUT a capacity test. Used only for the vernacular override, where
     * the language-capable office must keep the case regardless of load, and for administrative
     * transfers that have already been authorised elsewhere.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OfficeThresholdConfig o SET o.currentCount = o.currentCount + 1 "
         + "WHERE o.officeId = :officeId AND o.active = true")
    int incrementUnconditionally(@Param("officeId") String officeId);

    /**
     * Releases one unit of capacity, floored at zero so a double-release cannot drive the counter
     * negative and hand an office unlimited apparent capacity.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OfficeThresholdConfig o SET o.currentCount = o.currentCount - 1 "
         + "WHERE o.officeId = :officeId AND o.currentCount > 0")
    int releaseCapacity(@Param("officeId") String officeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OfficeThresholdConfig o SET o.currentCount = 0 WHERE o.department = :department AND o.active = true")
    int resetCountersForDepartment(@Param("department") String department);
}
