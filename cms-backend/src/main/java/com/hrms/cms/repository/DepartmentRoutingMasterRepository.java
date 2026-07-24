package com.hrms.cms.repository;

import com.hrms.cms.entity.DepartmentRoutingMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DepartmentRoutingMasterRepository extends JpaRepository<DepartmentRoutingMaster, Long> {
    Optional<DepartmentRoutingMaster> findByEntityNameIgnoreCaseAndActiveTrue(String entityName);
    List<DepartmentRoutingMaster> findByActiveTrueOrderByEntityNameAsc();
    List<DepartmentRoutingMaster> findByDepartmentAndActiveTrueOrderByEntityNameAsc(String department);
    List<DepartmentRoutingMaster> findByRegistrationStatusAndActiveTrue(String registrationStatus);
}
