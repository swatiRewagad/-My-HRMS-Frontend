package com.hrms.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the acting AA user and role from the request, server-side only.
 *
 * Why this exists: {@code AppealWorkflowService.performAction} used to read both {@code actor} and
 * {@code actorRole} out of the request body, and enforced its role matrix only when {@code actorRole}
 * was non-blank. The Angular client never sent it, so in practice the AA role check was skipped on
 * every real request and any caller could invoke any action. Attribution was spoofable for the same
 * reason: the recorded actor was whatever the caller typed.
 *
 * The resolution order deliberately differs from {@link AaRoleGuardAspect}: the JWT wins, and the
 * X-User-* dev headers are honoured only under the dev-local profile. The E2E suites drive the API
 * with those headers, so they stay usable locally, while a deployed instance cannot be talked into
 * accepting a self-declared role.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AaIdentityResolver {

    /** Roles permitted to act inside the AA module. AA_ADMIN is included for override/reassign. */
    public static final Set<String> AA_ROLES =
            Set.of("AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN");

    private final ObjectMapper objectMapper;

    /**
     * True only under dev-local. Injected rather than read from Environment so the flag is visible
     * in one place; the enforcing profile leaves it false and headers are then ignored entirely.
     */
    @Value("${cms.security.allow-dev-identity-headers:false}")
    private boolean allowDevIdentityHeaders;

    /**
     * The acting AA role for the current request, or null when the caller holds none.
     *
     * Returns null rather than a default: a permissive fallback here is precisely the bug being
     * fixed, and callers reject the request instead.
     */
    public String resolveAaRole() {
        Set<String> roles = resolveRoles();
        for (String role : roles) {
            if (AA_ROLES.contains(role)) {
                return role;
            }
        }
        return null;
    }

    /** The acting user id, or null when it cannot be established. */
    public String resolveActor() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        Map<String, Object> claims = decodeClaims(request);
        if (claims != null) {
            String fromToken = firstNonBlank(
                    asString(claims.get("preferred_username")),
                    asString(claims.get("sub")));
            if (fromToken != null) {
                return fromToken;
            }
        }

        return allowDevIdentityHeaders ? blankToNull(request.getHeader("X-User-Id")) : null;
    }

    /** The reviewer tier ("1"/"2") for an AA_REVIEWER, or null. Claim-only: never client-declared. */
    public String resolveReviewerTier() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Map<String, Object> claims = decodeClaims(request);
        if (claims != null) {
            String tier = asString(claims.get("reviewer_tier"));
            if (tier != null && !tier.isBlank()) {
                return tier.trim();
            }
        }
        return allowDevIdentityHeaders ? blankToNull(request.getHeader("X-Reviewer-Tier")) : null;
    }

    public Set<String> resolveRoles() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return Collections.emptySet();
        }

        Set<String> roles = new LinkedHashSet<>();

        // The token is authoritative and is read first.
        Map<String, Object> claims = decodeClaims(request);
        if (claims != null) {
            Object realmAccess = claims.get("realm_access");
            if (realmAccess instanceof Map<?, ?> realmMap) {
                Object rolesObj = realmMap.get("roles");
                if (rolesObj instanceof List<?> roleList) {
                    for (Object r : roleList) {
                        if (r != null) {
                            roles.add(r.toString().trim());
                        }
                    }
                }
            }
        }

        if (!roles.isEmpty()) {
            return roles;
        }

        if (!allowDevIdentityHeaders) {
            return roles;
        }

        String rolesHeader = request.getHeader("X-User-Roles");
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            for (String role : rolesHeader.split(",")) {
                if (!role.isBlank()) {
                    roles.add(role.trim());
                }
            }
            return roles;
        }

        String singleRole = request.getHeader("X-User-Role");
        if (singleRole != null && !singleRole.isBlank()) {
            roles.add(singleRole.trim());
        }
        return roles;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeClaims(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String[] parts = authHeader.substring(7).split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            log.debug("Could not decode JWT claims for AA identity: {}", e.getMessage());
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
