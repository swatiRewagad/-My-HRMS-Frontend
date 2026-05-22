package com.hrms.cms.repository;

import com.hrms.cms.entity.CmsUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CmsUserRepository extends JpaRepository<CmsUser, Long> {
    Optional<CmsUser> findByPhone(String phone);
    boolean existsByPhone(String phone);
}
