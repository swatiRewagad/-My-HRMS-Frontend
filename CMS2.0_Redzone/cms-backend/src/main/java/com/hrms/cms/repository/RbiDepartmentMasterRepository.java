package com.hrms.cms.repository;

import com.hrms.cms.entity.RbiDepartmentMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RbiDepartmentMasterRepository extends JpaRepository<RbiDepartmentMaster, Long> {

    List<RbiDepartmentMaster> findByIsActiveOrderByDisplayOrderAsc(String isActive);

    Optional<RbiDepartmentMaster> findByDeptCodeIgnoreCase(String deptCode);

    Optional<RbiDepartmentMaster> findByDeptNameIgnoreCase(String deptName);
}
