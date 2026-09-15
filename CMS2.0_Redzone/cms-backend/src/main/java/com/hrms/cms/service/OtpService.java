package com.hrms.cms.service;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.entity.OtpAttempt;
import com.hrms.cms.repository.OtpAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpAttemptRepository otpAttemptRepository;
    private final AuthSecurityProperties authProps;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * UST8: seconds the caller must still wait before another OTP may be requested, or 0 when a request
     * is allowed now. The browser timer alone is no control — a scripted client simply skips it.
     */
    public int resendCooldownRemaining(String mobileNumber) {
        int cooldown = authProps.getOtp().getResendCooldownSeconds();
        if (cooldown <= 0) return 0;
        return otpAttemptRepository.findTopByMobileNumberOrderByCreatedAtDesc(mobileNumber)
                .map(last -> {
                    long elapsed = Duration.between(last.getCreatedAt(), LocalDateTime.now()).toSeconds();
                    return elapsed >= cooldown ? 0 : (int) (cooldown - elapsed);
                })
                .orElse(0);
    }

    @Transactional
    public String generateOtp(String mobileNumber, String sessionId, String channel, String email) {
        // UST8: without this an earlier OTP stays valid alongside the new one, so a regenerated code
        // does not actually retire the code that was already sent.
        otpAttemptRepository.invalidateActiveOtps(mobileNumber, LocalDateTime.now());

        String otp = authProps.getOtp().isDevAutoPopulate() ? "123456" : generateSecureOtp();
        String otpHash = hashValue(otp);

        OtpAttempt attempt = OtpAttempt.builder()
                .mobileNumber(mobileNumber)
                .otpHash(otpHash)
                .channel(channel)
                .email(email)
                .sessionId(sessionId)
                .used(false)
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(authProps.getOtp().getExpiryMinutes()))
                .build();

        otpAttemptRepository.save(attempt);
        log.info("OTP generated for mobile: ****{} via {}", mobileNumber.substring(mobileNumber.length() - 4), channel);
        return otp;
    }

    @Transactional
    public OtpVerificationResult verifyOtp(String mobileNumber, String otpInput) {
        Optional<OtpAttempt> activeOtp = otpAttemptRepository
                .findTopByMobileNumberAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        mobileNumber, LocalDateTime.now());

        if (activeOtp.isEmpty()) {
            return OtpVerificationResult.EXPIRED_OR_NOT_FOUND;
        }

        OtpAttempt attempt = activeOtp.get();

        if (attempt.getAttemptCount() >= authProps.getOtp().getMaxVerifyAttemptsPerOtp()) {
            attempt.setUsed(true);
            otpAttemptRepository.save(attempt);
            return OtpVerificationResult.MAX_ATTEMPTS_EXCEEDED;
        }

        attempt.setAttemptCount(attempt.getAttemptCount() + 1);

        String inputHash = hashValue(otpInput);
        if (inputHash.equals(attempt.getOtpHash())) {
            attempt.setUsed(true);
            attempt.setUsedAt(LocalDateTime.now());
            otpAttemptRepository.save(attempt);
            return OtpVerificationResult.SUCCESS;
        }

        otpAttemptRepository.save(attempt);
        return OtpVerificationResult.INVALID;
    }

    public boolean isRateLimitedByMobile(String mobileNumber) {
        long recentCount = otpAttemptRepository.countRecentByMobile(
                mobileNumber, LocalDateTime.now().minusHours(1));
        return recentCount >= authProps.getRateLimit().getOtpRequestsPerMobilePerHour();
    }

    private String generateSecureOtp() {
        int length = authProps.getOtp().getLength();
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }

    private String hashValue(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public enum OtpVerificationResult {
        SUCCESS,
        INVALID,
        EXPIRED_OR_NOT_FOUND,
        MAX_ATTEMPTS_EXCEEDED
    }
}
