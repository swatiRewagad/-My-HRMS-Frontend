package com.hrms.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the caller's identity from the request, server-side only.
 *
 * The bearer token is ALWAYS authoritative. X-User-* headers are a dev-local convenience and are
 * honoured only when {@code cms.security.allow-dev-identity-headers} is true, matching
 * {@link AaIdentityResolver}. Header-first resolution was a privilege-escalation hole: this class
 * feeds {@code RevocationCheckFilter} (so {@code X-User-Id} naming any other user defeated
 * revocation entirely) and the PII-reveal authorisation check (so {@code X-User-Roles} could mint a
 * reveal-capable role), in every profile including production.
 *
 * Unlike the role-guard aspect this never falls back to a permissive default: a caller whose
 * identity cannot be established gets no identity, and the callers of this class reject the
 * request. Attribution on an append-only thread has to be trustworthy (UST857).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestIdentityResolver {

    private static final Set<String> RE_ROLES = Set.of("RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN");

    private final ObjectMapper objectMapper;

    /** True only under dev-local; the enforcing profile leaves it false and headers are ignored. */
    @Value("${cms.security.allow-dev-identity-headers:false}")
    private boolean allowDevIdentityHeaders;

    /** Returns null when no identity could be established. */
    public RequestIdentity resolve(HttpServletRequest request) {
        Set<String> roles = extractRoles(request);
        Map<String, Object> claims = decodeClaims(request);

        // The token wins. The header is a dev-local fallback only: trusting it first let any caller
        // impersonate another user id, which the revocation filter then cleared as "not revoked".
        String userId = firstNonBlank(
                claims == null ? null : asString(claims.get("preferred_username")),
                claims == null ? null : asString(claims.get("sub")),
                allowDevIdentityHeaders ? request.getHeader("X-User-Id") : null);

        if (userId == null) {
            return null;
        }

        String displayName = firstNonBlank(
                claims == null ? null : asString(claims.get("name")),
                allowDevIdentityHeaders ? request.getHeader("X-User-Name") : null,
                userId);

        boolean isRe = roles.stream().anyMatch(RE_ROLES::contains);

        return RequestIdentity.builder()
                .userId(userId)
                .displayName(displayName)
                .primaryRole(roles.isEmpty() ? null : roles.iterator().next())
                .roles(roles)
                .side(isRe ? "RE" : "RBI")
                .entityCode(isRe ? resolveEntityCode(request, claims) : null)
                .build();
    }

    /**
     * The JWT claim wins over the header. A header alone would let an RE caller name another
     * entity's code and read its threads, so it is only honoured when the token carries no claim
     * (the dev-header mode the rest of this codebase already supports).
     */
    private String resolveEntityCode(HttpServletRequest request, Map<String, Object> claims) {
        if (claims != null) {
            String fromToken = asString(claims.get("entity_code"));
            if (fromToken != null && !fromToken.isBlank()) {
                return fromToken.trim();
            }
        }
        if (!allowDevIdentityHeaders) {
            return null;
        }
        return firstNonBlank(request.getHeader("X-Entity-Code"), request.getHeader("X-User-Entity"));
    }

    private Set<String> extractRoles(HttpServletRequest request) {
        Set<String> roles = new HashSet<>();

        // The token is authoritative and is read FIRST. Returning header roles ahead of the token let
        // a caller mint any role they liked (e.g. a reveal-capable role on the PII endpoint).
        Map<String, Object> claims = decodeClaims(request);
        if (claims != null) {
            Object realmAccess = claims.get("realm_access");
            if (realmAccess instanceof Map<?, ?> realmMap) {
                Object rolesObj = realmMap.get("roles");
                if (rolesObj instanceof List<?> roleList) {
                    for (Object r : roleList) {
                        if (r != null) {
                            roles.add(r.toString());
                        }
                    }
                }
            }
        }

        if (!roles.isEmpty() || !allowDevIdentityHeaders) {
            return roles;
        }

        String rolesHeader = request.getHeader("X-User-Roles");
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            for (String role : rolesHeader.split(",")) {
                roles.add(role.trim());
            }
            return roles;
        }

        String singleRole = request.getHeader("X-User-Role");
        if (singleRole != null && !singleRole.isBlank()) {
            roles.add(singleRole.trim());
        }
        return roles;
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
            log.debug("Could not decode JWT claims: {}", e.getMessage());
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
