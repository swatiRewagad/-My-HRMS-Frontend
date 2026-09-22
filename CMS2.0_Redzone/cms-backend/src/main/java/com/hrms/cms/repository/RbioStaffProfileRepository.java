package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioStaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RbioStaffProfileRepository extends JpaRepository<RbioStaffProfile, Long> {

    Optional<RbioStaffProfile> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<RbioStaffProfile> findByUserIdIn(List<String> userIds);

    List<RbioStaffProfile> findByOfficeCodeAndIsActive(String officeCode, String isActive);

    List<RbioStaffProfile> findByPrimaryRoleAndIsActive(String primaryRole, String isActive);
}
