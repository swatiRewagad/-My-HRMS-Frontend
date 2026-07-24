package com.hrms.cms.repository;

import com.hrms.cms.entity.OtpAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OtpAttemptRepository extends JpaRepository<OtpAttempt, Long> {

    Optional<OtpAttempt> findTopByMobileNumberAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String mobileNumber, LocalDateTime now);

    @Query("SELECT COUNT(o) FROM OtpAttempt o WHERE o.mobileNumber = :mobile AND o.createdAt > :since")
    long countRecentByMobile(@Param("mobile") String mobileNumber, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(o) FROM OtpAttempt o WHERE o.mobileNumber = :mobile AND o.used = false AND o.attemptCount > 0 AND o.createdAt > :since")
    long countFailedVerifications(@Param("mobile") String mobileNumber, @Param("since") LocalDateTime since);

    List<OtpAttempt> findByExpiresAtBefore(LocalDateTime cutoff);
}
