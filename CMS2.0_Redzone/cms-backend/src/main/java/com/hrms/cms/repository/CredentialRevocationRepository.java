package com.hrms.cms.repository;

import com.hrms.cms.entity.CredentialRevocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CredentialRevocationRepository extends JpaRepository<CredentialRevocation, Long> {

    boolean existsByUsernameAndActiveTrue(String username);

    Optional<CredentialRevocation> findFirstByUsernameAndActiveTrueOrderByRevokedAtDesc(String username);

    List<CredentialRevocation> findByActiveTrue();

    Page<CredentialRevocation> findByOrderByRevokedAtDesc(Pageable pageable);

    List<CredentialRevocation> findByUsernameOrderByRevokedAtDesc(String username);
}
