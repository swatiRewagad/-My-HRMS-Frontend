package com.hrms.cms.service;

import com.hrms.cms.entity.CitizenEmailVerification;
import com.hrms.cms.repository.CitizenEmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String SESSION_SECRET = System.getenv().getOrDefault(
            "CMS_SESSION_SECRET", "cms-default-secret-change-in-production");
    private static final long SESSION_TIMEOUT_MS = 15 * 60 * 1000;

    private final Map<String, SessionData> activeSessions = new ConcurrentHashMap<>();

    public String createSession(String mobileNumber) {
        String tokenId = UUID.randomUUID().toString();
        String signature = sign(tokenId + ":" + mobileNumber);
        String token = tokenId + "." + signature;

        activeSessions.put(token, new SessionData(mobileNumber, Instant.now()));
        log.info("Session created for mobile: {}****", mobileNumber.substring(0, 4));
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
        log.info("Email verified: {} for mobile {}****", v.getEmail(), v.getMobileNumber().substring(0, 4));
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
