package com.hrms.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the caller's identity from the request, server-side only.
 *
 * Mirrors the role-extraction order already used by {@link ReRoleGuardAspect} (X-User-Roles,
 * X-User-Role, then the Authorization bearer token) so behaviour is consistent with the rest of
 * the codebase, and additionally resolves the user id and display name.
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

    /** Returns null when no identity could be established. */
    public RequestIdentity resolve(HttpServletRequest request) {
        Set<String> roles = extractRoles(request);
        Map<String, Object> claims = decodeClaims(request);

        String userId = firstNonBlank(
                request.getHeader("X-User-Id"),
                claims == null ? null : asString(claims.get("preferred_username")),
                claims == null ? null : asString(claims.get("sub")));

        if (userId == null) {
            return null;
        }

        String displayName = firstNonBlank(
                request.getHeader("X-User-Name"),
                claims == null ? null : asString(claims.get("name")),
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
        return firstNonBlank(request.getHeader("X-Entity-Code"), request.getHeader("X-User-Entity"));
    }

    private Set<String> extractRoles(HttpServletRequest request) {
        Set<String> roles = new HashSet<>();

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
            return roles;
        }

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
