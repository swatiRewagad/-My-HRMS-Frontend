package com.hrms.cms.repository;

import com.hrms.cms.entity.WfOfficerPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WfOfficerPoolRepository extends JpaRepository<WfOfficerPool, Long> {
    Optional<WfOfficerPool> findByUserId(String userId);
    List<WfOfficerPool> findByRoleGroupAndActiveTrue(String roleGroup);
    List<WfOfficerPool> findByRegionalOfficeAndActiveTrue(String regionalOffice);
}
