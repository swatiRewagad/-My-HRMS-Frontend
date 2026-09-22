package com.hrms.cms.service;

import com.hrms.cms.entity.CredentialRevocation;
import com.hrms.cms.repository.CredentialRevocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immediate credential revocation (UST877).
 *
 * Revocation is three things, and skipping any one leaves access alive:
 *   1. disable the account, so Keycloak issues no new tokens;
 *   2. terminate active sessions, so refresh tokens cannot mint replacements;
 *   3. record the username locally, so an access token already in the wild is rejected here before
 *      its natural expiry.
 *
 * Step 3 is what makes this immediate. This service validates JWTs offline against the realm's
 * public key and never calls Keycloak per request, so without a local list a revoked user would keep
 * working for the remaining token lifetime.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialRevocationService {

    public static final String REASON_OFFBOARDING = "OFFBOARDING";
    public static final String REASON_DEACTIVATION = "DEACTIVATION";
    public static final String REASON_COMPROMISE = "SUSPECTED_COMPROMISE";
    public static final String REASON_ADMIN = "ADMIN_ACTION";

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final CredentialRevocationRepository revocationRepository;
    private final KeycloakUserService keycloakUserService;
    private final CepcAuditService auditService;

    /**
     * Revoked usernames, refreshed on a short TTL.
     *
     * The alternative is a database read on every authenticated request. The TTL bounds how long a
     * just-revoked token can still be accepted; it is deliberately short.
     */
    private volatile Set<String> revokedCache = Set.of();
    private final AtomicLong cacheExpiresAtNanos = new AtomicLong(0);

    public record RevocationResult(boolean accountDisabled,
                                   boolean sessionsTerminated,
                                   int sessionsTerminatedCount,
                                   boolean fullySuccessful,
                                   String detail) {}

    @Transactional
    public RevocationResult revoke(String username, String reason, String notes, String initiatedBy) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required to revoke access.");
        }
        String normalised = username.trim();

        String keycloakUserId = keycloakUserService.findUserId(normalised);
        int sessionCount = 0;
        boolean disabled = false;
        boolean loggedOut = false;
        StringBuilder detail = new StringBuilder();

        if (keycloakUserId == null) {
            // The local block is still recorded: a user missing from the realm must not silently
            // result in "not revoked".
            detail.append("User not found in Keycloak realm; local revocation recorded. ");
            log.warn("Revocation for {} could not reach Keycloak - user not found", normalised);
        } else {
            sessionCount = keycloakUserService.countActiveSessions(keycloakUserId);
            disabled = keycloakUserService.setUserEnabled(keycloakUserId, false);
            if (!disabled) {
                detail.append("Failed to disable the Keycloak account. ");
            }
            loggedOut = keycloakUserService.logoutAllSessions(keycloakUserId);
            if (!loggedOut) {
                detail.append("Failed to terminate Keycloak sessions. ");
            }
        }

        CredentialRevocation revocation = revocationRepository.save(CredentialRevocation.builder()
                .username(normalised)
                .keycloakUserId(keycloakUserId)
                .reason(reason == null ? REASON_ADMIN : reason)
                .notes(notes)
                .initiatedBy(initiatedBy == null ? "SYSTEM" : initiatedBy)
                .revokedAt(LocalDateTime.now())
                .active(true)
                .accountDisabled(disabled)
                .sessionsTerminated(loggedOut)
                .sessionsTerminatedCount(Math.max(sessionCount, 0))
                .failureDetail(detail.length() == 0 ? null : detail.toString().trim())
                .build());

        invalidateCache();

        auditService.logActionAsync("N/A", "CREDENTIALS_REVOKED", initiatedBy, "ADMIN",
                "Revoked access for " + normalised + " (" + revocation.getReason() + ")"
                        + (notes == null || notes.isBlank() ? "" : ": " + notes),
                null, null, null);

        boolean fully = keycloakUserId != null && disabled && loggedOut;
        log.info("Credential revocation for {} by {}: disabled={} sessionsTerminated={} count={}",
                normalised, initiatedBy, disabled, loggedOut, sessionCount);

        return new RevocationResult(disabled, loggedOut, Math.max(sessionCount, 0), fully,
                detail.length() == 0 ? null : detail.toString().trim());
    }

    @Transactional
    public void restore(String username, String restoredBy) {
        String normalised = username == null ? null : username.trim();
        if (normalised == null || normalised.isBlank()) {
            throw new IllegalArgumentException("Username is required to restore access.");
        }

        List<CredentialRevocation> active = revocationRepository.findByUsernameOrderByRevokedAtDesc(normalised)
                .stream().filter(CredentialRevocation::isActive).toList();

        for (CredentialRevocation revocation : active) {
            revocation.setActive(false);
            revocation.setRestoredAt(LocalDateTime.now());
            revocation.setRestoredBy(restoredBy);
            revocationRepository.save(revocation);
        }

        String keycloakUserId = keycloakUserService.findUserId(normalised);
        if (keycloakUserId != null) {
            keycloakUserService.setUserEnabled(keycloakUserId, true);
        }

        invalidateCache();

        auditService.logActionAsync("N/A", "CREDENTIALS_RESTORED", restoredBy, "ADMIN",
                "Restored access for " + normalised, null, null, null);
        log.info("Credential access restored for {} by {}", normalised, restoredBy);
    }

    /** True when this username currently has access revoked. */
    public boolean isRevoked(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return currentRevoked().contains(username.trim());
    }

    private Set<String> currentRevoked() {
        long now = System.nanoTime();
        if (now < cacheExpiresAtNanos.get()) {
            return revokedCache;
        }
        try {
            Set<String> fresh = new HashSet<>();
            for (CredentialRevocation r : revocationRepository.findByActiveTrue()) {
                fresh.add(r.getUsername());
            }
            revokedCache = fresh;
            cacheExpiresAtNanos.set(System.nanoTime() + CACHE_TTL.toNanos());
        } catch (Exception e) {
            // Keep serving the previous list rather than failing open on a transient DB error.
            log.error("Could not refresh the revocation list, reusing the cached copy: {}", e.getMessage());
        }
        return revokedCache;
    }

    public void invalidateCache() {
        cacheExpiresAtNanos.set(0);
    }
}
