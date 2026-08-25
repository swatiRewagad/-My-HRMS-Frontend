package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfficerAvailabilityRepository extends JpaRepository<OfficerAvailability, Long> {
    Optional<OfficerAvailability> findByUserIdAndRole(String userId, String role);
    List<OfficerAvailability> findByRole(String role);
    List<OfficerAvailability> findByRoleAndActiveTrue(String role);
    List<OfficerAvailability> findByRoleAndActiveTrueAndOnLeaveFalse(String role);
}
