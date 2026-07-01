package com.hrms.cms.repository;

import com.hrms.cms.entity.CitizenEmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CitizenEmailVerificationRepository extends JpaRepository<CitizenEmailVerification, Long> {

    Optional<CitizenEmailVerification> findByMobileNumberAndEmailAndVerifiedTrue(
            String mobileNumber, String email);

    Optional<CitizenEmailVerification> findByVerificationTokenAndVerifiedFalse(String token);

    boolean existsByMobileNumberAndEmailAndVerifiedTrue(String mobileNumber, String email);
}
