package com.hrms.cms.repository;

import com.hrms.cms.entity.EntityUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntityUserRepository extends JpaRepository<EntityUser, Long> {

    Optional<EntityUser> findByUserIdAndEntityCode(String userId, String entityCode);

    List<EntityUser> findByEntityCodeAndActiveTrueOrderByDisplayNameAsc(String entityCode);

    List<EntityUser> findByEntityCodeAndReRoleAndActiveTrueOrderByDisplayNameAsc(String entityCode,
                                                                                String reRole);

    List<EntityUser> findByEntityCodeOrderByDisplayNameAsc(String entityCode);

    boolean existsByUserIdAndEntityCode(String userId, String entityCode);
}
