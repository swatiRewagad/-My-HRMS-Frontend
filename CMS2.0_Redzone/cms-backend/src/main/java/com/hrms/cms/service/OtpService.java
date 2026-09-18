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

    /**
     * Issues an OTP scoped to a session AND a channel, so two codes can be live at once (UST599).
     *
     * <p>Differs from {@link #generateOtp} in exactly one respect: invalidation is scoped to
     * {@code sessionId + channel} instead of to the mobile number. {@code generateOtp} retires every
     * live code for a mobile, which makes a dual-channel OTP impossible — the second code issued would
     * silently kill the first, so the user could never satisfy both inputs.
     *
     * <p>A separate method rather than a flag on the existing one: the citizen-login flow depends on
     * mobile-wide invalidation (UST8, a re-sent code must retire the previous one), and that behaviour
     * must not become conditional on a parameter a caller might get wrong.
     *
     * @param sessionId the upload-link token — unique per link and unused by any other query
     * @param channel   SMS or EMAIL; part of the lookup key, not merely metadata as elsewhere
     */
    @Transactional
    public String generateSessionOtp(String mobileNumber, String sessionId, String channel, String email) {
        otpAttemptRepository.invalidateActiveOtpsForSessionChannel(sessionId, channel, LocalDateTime.now());

        String otp = authProps.getOtp().isDevAutoPopulate() ? "123456" : generateSecureOtp();

        otpAttemptRepository.save(OtpAttempt.builder()
                .mobileNumber(mobileNumber)
                .otpHash(hashValue(otp))
                .channel(channel)
                .email(email)
                .sessionId(sessionId)
                .used(false)
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(authProps.getOtp().getExpiryMinutes()))
                .build());

        log.info("Session OTP generated for session {} via {}", sessionId, channel);
        return otp;
    }

    /**
     * Verifies one channel's OTP for a session (UST599).
     *
     * <p>Attempt counting, expiry and max-attempt lockout behave exactly as in {@link #verifyOtp} —
     * deliberately identical, so the two channels cannot be brute-forced under a weaker rule than the
     * login flow. Each channel carries its own attempt counter because they are separate rows.
     */
    @Transactional
    public OtpVerificationResult verifySessionOtp(String sessionId, String channel, String otpInput) {
        Optional<OtpAttempt> activeOtp = otpAttemptRepository
                .findTopBySessionIdAndChannelAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        sessionId, channel, LocalDateTime.now());

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

        if (hashValue(otpInput).equals(attempt.getOtpHash())) {
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
