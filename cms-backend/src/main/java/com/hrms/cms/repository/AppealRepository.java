package com.hrms.cms.repository;

import com.hrms.cms.entity.Appeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    Optional<Appeal> findByAppealNumber(String appealNumber);

    List<Appeal> findByOriginalComplaintNumber(String originalComplaintNumber);

    List<Appeal> findByStatusNotInOrderByCreatedAtDesc(List<String> excludeStatuses);

    List<Appeal> findByAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(String assignedOfficer, List<String> excludeStatuses);

    List<Appeal> findByAssignedRoleAndStatusNotInOrderByCreatedAtDesc(String assignedRole, List<String> excludeStatuses);

    List<Appeal> findByStatus(String status);
}
