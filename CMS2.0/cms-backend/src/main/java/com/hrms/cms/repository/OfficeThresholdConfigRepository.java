package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficeThresholdConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OfficeThresholdConfigRepository extends JpaRepository<OfficeThresholdConfig, Long> {
    Optional<OfficeThresholdConfig> findByOfficeId(String officeId);
    List<OfficeThresholdConfig> findByActiveTrueOrderByOverflowSequenceOrderAsc();
    List<OfficeThresholdConfig> findByDepartmentAndActiveTrueOrderByOverflowSequenceOrderAsc(String department);
    long countByCurrentCountGreaterThanEqualAndActiveTrue(int threshold);
}
