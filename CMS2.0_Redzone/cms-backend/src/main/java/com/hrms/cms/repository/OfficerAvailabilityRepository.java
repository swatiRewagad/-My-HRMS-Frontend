package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OfficerAvailabilityRepository extends JpaRepository<OfficerAvailability, Long> {

    Optional<OfficerAvailability> findByUserIdAndRole(String userId, String role);
}
