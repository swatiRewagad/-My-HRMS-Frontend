package com.hrms.cms.repository;

import com.hrms.cms.entity.ReportAccessRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportAccessRoleRepository extends JpaRepository<ReportAccessRole, Long> {

    Optional<ReportAccessRole> findByReportTypeAndRoleName(String reportType, String roleName);

    List<ReportAccessRole> findByRoleNameIn(List<String> roleNames);

    List<ReportAccessRole> findAllByOrderByReportTypeAscRoleNameAsc();
}
