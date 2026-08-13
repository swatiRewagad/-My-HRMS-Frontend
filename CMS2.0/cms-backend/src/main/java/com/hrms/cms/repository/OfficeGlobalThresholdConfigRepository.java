package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficeGlobalThresholdConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfficeGlobalThresholdConfigRepository extends JpaRepository<OfficeGlobalThresholdConfig, Integer> {
}
