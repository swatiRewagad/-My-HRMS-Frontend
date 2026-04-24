package com.hrms.ecm.repository;

import com.hrms.ecm.entity.EcmUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EcmUserRepository extends JpaRepository<EcmUser, Long> {
    Optional<EcmUser> findByUsername(String username);
}
