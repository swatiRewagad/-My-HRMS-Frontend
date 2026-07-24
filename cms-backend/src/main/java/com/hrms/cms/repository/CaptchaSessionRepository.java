package com.hrms.cms.repository;

import com.hrms.cms.entity.CaptchaSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CaptchaSessionRepository extends JpaRepository<CaptchaSession, Long> {

    Optional<CaptchaSession> findByCaptchaTokenAndUsedFalseAndExpiresAtAfter(
            String captchaToken, LocalDateTime now);

    List<CaptchaSession> findByExpiresAtBefore(LocalDateTime cutoff);
}
