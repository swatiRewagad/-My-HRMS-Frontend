package com.hrms.cms.repository;

import com.hrms.cms.entity.LoginCooloff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoginCooloffRepository extends JpaRepository<LoginCooloff, Long> {

    Optional<LoginCooloff> findByFingerprintHashAndClientIp(String fingerprintHash, String clientIp);

    Optional<LoginCooloff> findByMobileNumber(String mobileNumber);

    Optional<LoginCooloff> findByFingerprintHashAndClientIpAndCooloffUntilAfter(
            String fingerprintHash, String clientIp, LocalDateTime now);

    Optional<LoginCooloff> findByMobileNumberAndCooloffUntilAfter(
            String mobileNumber, LocalDateTime now);
}
