package com.hrms.cms.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * AOP aspect that enforces role-based access control on RE (Regulated Entity) portal endpoints.
 * Checks the current user's roles from the JWT token (passed via request headers)
 * against the allowed roles specified in {@link ReRoleGuard}.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ReRoleGuardAspect {

    private final ObjectMapper objectMapper;

    @Around("@annotation(reRoleGuard)")
    public Object checkRole(ProceedingJoinPoint joinPoint, ReRoleGuard reRoleGuard) throws Throwable {
        String[] allowedRoles = reRoleGuard.roles();

        Set<String> userRoles = extractUserRoles();

        if (userRoles.isEmpty()) {
            log.debug("No roles found in request - allowing through (dev mode or missing token)");
            return joinPoint.proceed();
        }

        for (String allowedRole : allowedRoles) {
            if (userRoles.contains(allowedRole)) {
                return joinPoint.proceed();
            }
        }

        log.warn("RE Portal access denied: user roles {} do not include any of {}", userRoles, Arrays.toString(allowedRoles));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Access denied: insufficient role permissions for this RE portal action");
    }

    /**
     * Extracts user roles from the request.
     * Checks for:
     * 1. X-User-Roles header (comma-separated roles)
     * 2. X-User-Role header (single role, for backward compat)
     * 3. Authorization Bearer token (JWT decode for realm_access.roles)
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractUserRoles() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return Collections.emptySet();

        HttpServletRequest request = attrs.getRequest();
        Set<String> roles = new HashSet<>();

        // Check X-User-Roles header (comma-separated)
        String rolesHeader = request.getHeader("X-User-Roles");
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            for (String role : rolesHeader.split(",")) {
                roles.add(role.trim());
            }
            return roles;
        }

        // Check single X-User-Role header
        String singleRole = request.getHeader("X-User-Role");
        if (singleRole != null && !singleRole.isBlank()) {
            roles.add(singleRole.trim());
            return roles;
        }

        // Try to decode JWT from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                    Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

                    // Extract realm_access.roles (Keycloak standard)
                    Object realmAccess = claims.get("realm_access");
                    if (realmAccess instanceof Map) {
                        Object rolesObj = ((Map<?, ?>) realmAccess).get("roles");
                        if (rolesObj instanceof List) {
                            for (Object r : (List<?>) rolesObj) {
                                roles.add(r.toString());
                            }
                        }
                    }

                    // Also check resource_access for client-specific roles
                    Object resourceAccess = claims.get("resource_access");
                    if (resourceAccess instanceof Map) {
                        for (Object clientRoles : ((Map<?, ?>) resourceAccess).values()) {
                            if (clientRoles instanceof Map) {
                                Object clientRolesList = ((Map<?, ?>) clientRoles).get("roles");
                                if (clientRolesList instanceof List) {
                                    for (Object r : (List<?>) clientRolesList) {
                                        roles.add(r.toString());
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to decode JWT for role extraction: {}", e.getMessage());
            }
        }

        return roles;
    }
}
