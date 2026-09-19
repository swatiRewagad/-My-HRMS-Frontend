package com.hrms.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who is making the current HTTP request, as far as cms-backend can tell.
 *
 * <p>TODO: the bearer token is decoded but never verified — no signature check, no expiry check —
 * because SecurityConfig permits all requests and the backend is not an OAuth2 resource server. Every
 * value returned here is therefore spoofable by the caller. Replace with the authenticated principal
 * once the backend validates JWTs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CallerIdentity {

    private final ObjectMapper objectMapper;

    /**
     * The acting user: {@code X-User-Id} header if the caller sent one, otherwise the token's
     * {@code preferred_username}. Null when neither is present.
     */
    public String username() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        String header = request.getHeader("X-User-Id");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }

        Map<String, Object> claims = claims(request);
        Object preferred = claims.get("preferred_username");
        if (preferred == null) {
            preferred = claims.get("username");
        }
        return preferred == null || preferred.toString().isBlank() ? null : preferred.toString().trim();
    }

    /**
     * The caller's roles, from {@code X-User-Roles} (comma separated), else {@code X-User-Role}, else the
     * token's {@code realm_access.roles} plus every {@code resource_access.*.roles}. Role names carry no
     * {@code ROLE_} prefix. An empty set means "could not tell", not "no roles".
     */
    public Set<String> roles() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return Collections.emptySet();
        }

        Set<String> roles = new HashSet<>();

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
            return roles;
        }

        Map<String, Object> claims = claims(request);
        collectRoles(claims.get("realm_access"), roles);
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> clients) {
            clients.values().forEach(clientRoles -> collectRoles(clientRoles, roles));
        }
        return roles;
    }

    private void collectRoles(Object accessBlock, Set<String> into) {
        if (accessBlock instanceof Map<?, ?> block && block.get("roles") instanceof List<?> list) {
            list.forEach(role -> into.add(role.toString()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> claims(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Collections.emptyMap();
        }
        try {
            String[] parts = authHeader.substring(7).split("\\.");
            if (parts.length < 2) {
                return Collections.emptyMap();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            log.debug("Failed to decode JWT payload: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
