package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioStatusRoleVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RbioStatusRoleVisibilityRepository extends JpaRepository<RbioStatusRoleVisibility, Long> {

    List<RbioStatusRoleVisibility> findByRoleNameOrderByDisplayOrderAsc(String roleName);

    boolean existsByStatusCodeAndRoleName(String statusCode, String roleName);
}
