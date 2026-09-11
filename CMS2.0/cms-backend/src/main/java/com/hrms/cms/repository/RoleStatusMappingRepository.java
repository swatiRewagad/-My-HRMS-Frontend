package com.hrms.cms.repository;

import com.hrms.cms.entity.RoleStatusMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleStatusMappingRepository extends JpaRepository<RoleStatusMapping, Long> {

    List<RoleStatusMapping> findByRoleNameOrderBySequenceAsc(String roleName);
}
