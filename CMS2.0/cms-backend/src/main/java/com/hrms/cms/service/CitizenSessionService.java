package com.hrms.cms.service;

import com.hrms.cms.entity.CitizenEmailVerification;
import com.hrms.cms.repository.CitizenEmailVerificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CitizenSessionService {

    private final CitizenEmailVerificationRepository emailVerificationRepository;

    private static final String SESSION_SECRET;
    static {
        String secret = System.getenv("CMS_SESSION_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = UUID.randomUUID().toString();
        }
        SESSION_SECRET = secret;
    }
    private static final long SESSION_TIMEOUT_MS = 15 * 60 * 1000;
    private static final int MAX_SESSIONS = 100_000;

    private final Map<String, SessionData> activeSessions = new ConcurrentHashMap<>();

    @PostConstruct
    void warnIfDefaultSecret() {
        if (System.getenv("CMS_SESSION_SECRET") == null) {
            log.warn("CMS_SESSION_SECRET not set — using random ephemeral secret. Sessions will not survive restarts.");
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void evictExpiredSessions() {
        long now = Instant.now().toEpochMilli();
        activeSessions.entrySet().removeIf(e ->
                now - e.getValue().lastActivity().toEpochMilli() > SESSION_TIMEOUT_MS);
    }

    public String createSession(String mobileNumber) {
        if (activeSessions.size() >= MAX_SESSIONS) {
            evictExpiredSessions();
        }
        String tokenId = UUID.randomUUID().toString();
        String signature = sign(tokenId + ":" + mobileNumber);
        String token = tokenId + "." + signature;

        activeSessions.put(token, new SessionData(mobileNumber, Instant.now()));
        log.info("Session created for mobile: ****{}", mobileNumber.substring(mobileNumber.length() - 4));
        return token;
    }

    public boolean isSessionValid(String token) {
        if (token == null || token.isBlank()) return false;

        SessionData session = activeSessions.get(token);
        if (session == null) return false;

        if (Instant.now().toEpochMilli() - session.lastActivity().toEpochMilli() > SESSION_TIMEOUT_MS) {
            activeSessions.remove(token);
            return false;
        }

        activeSessions.put(token, new SessionData(session.mobileNumber(), Instant.now()));
        return true;
    }

    public void invalidateSession(String token) {
        activeSessions.remove(token);
    }

    public boolean isEmailVerifiedForMobile(String mobileNumber, String email) {
        return emailVerificationRepository.existsByMobileNumberAndEmailAndVerifiedTrue(mobileNumber, email);
    }

    @Transactional
    public String initiateEmailVerification(String mobileNumber, String email) {
        String token = UUID.randomUUID().toString();

        CitizenEmailVerification verification = CitizenEmailVerification.builder()
                .mobileNumber(mobileNumber)
                .email(email)
                .verificationToken(token)
                .verified(false)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        emailVerificationRepository.save(verification);
        return token;
    }

    @Transactional
    public boolean confirmEmailVerification(String token) {
        var verification = emailVerificationRepository
                .findByVerificationTokenAndVerifiedFalse(token);

        if (verification.isEmpty()) return false;

        CitizenEmailVerification v = verification.get();
        if (v.getExpiresAt().isBefore(LocalDateTime.now())) return false;

        v.setVerified(true);
        v.setVerifiedAt(LocalDateTime.now());
        emailVerificationRepository.save(v);
        log.info("Email verified for mobile: ****{}", v.getMobileNumber().substring(v.getMobileNumber().length() - 4));
        return true;
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SESSION_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign session token", e);
        }
    }

    private record SessionData(String mobileNumber, Instant lastActivity) {}
}
