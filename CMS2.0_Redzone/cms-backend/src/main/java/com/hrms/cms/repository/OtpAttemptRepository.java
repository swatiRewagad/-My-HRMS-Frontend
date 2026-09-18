package com.hrms.cms.repository;

import com.hrms.cms.entity.OtpAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    Optional<OtpAttempt> findTopByMobileNumberOrderByCreatedAtDesc(String mobileNumber);

    /** UST8: a newly issued OTP supersedes every earlier live one for the same mobile. */
    @Modifying
    @Query("UPDATE OtpAttempt o SET o.used = true, o.usedAt = :now " +
            "WHERE o.mobileNumber = :mobile AND o.used = false")
    int invalidateActiveOtps(@Param("mobile") String mobileNumber, @Param("now") LocalDateTime now);

    List<OtpAttempt> findByExpiresAtBefore(LocalDateTime cutoff);

    // ═══════════════════════════════════════════════════════════════════════════
    // Dual-channel OTP for the secure upload link (UST599)
    //
    // UST599 requires TWO independent codes — one emailed, one texted — and BOTH must
    // verify before the upload page opens. That is not expressible with the methods
    // above, because invalidateActiveOtps matches on MOBILE ALONE, ignoring channel
    // and sessionId. Issuing the email code and then the mobile code therefore retires
    // the first, so only the second could ever verify.
    //
    // These scope both invalidation and lookup by sessionId + channel. The upload-link
    // TOKEN is used as the sessionId: it is unique, already generated per link, and no
    // existing query reads sessionId — so this is additive and cannot affect the
    // citizen-login OTP flow.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Retires live OTPs for ONE channel of ONE session, leaving the other channel's code valid. */
    @Modifying
    @Query("UPDATE OtpAttempt o SET o.used = true, o.usedAt = :now "
         + "WHERE o.sessionId = :sessionId AND o.channel = :channel AND o.used = false")
    int invalidateActiveOtpsForSessionChannel(@Param("sessionId") String sessionId,
                                              @Param("channel") String channel,
                                              @Param("now") LocalDateTime now);

    Optional<OtpAttempt> findTopBySessionIdAndChannelAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String sessionId, String channel, LocalDateTime now);
}
