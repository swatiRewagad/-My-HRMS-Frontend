package com.hrms.cms.repository;

import com.hrms.cms.entity.CitizenConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CitizenConsentRepository extends JpaRepository<CitizenConsent, Long> {

    Optional<CitizenConsent> findTopByMobileNumberAndPurposeOrderByGrantedAtDesc(String mobileNumber, String purpose);
}
