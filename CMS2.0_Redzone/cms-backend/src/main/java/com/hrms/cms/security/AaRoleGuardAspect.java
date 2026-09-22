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
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

/**
 * AOP aspect that enforces role-based access control on AA (Appellate Authority) endpoints.
 * Checks the current user's roles from the JWT token (passed via request headers)
 * against the allowed roles specified in {@link AaRoleGuard}.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AaRoleGuardAspect {

    private final ObjectMapper objectMapper;

    /** True only under dev-local; the enforcing profile ignores X-User-Role(s) entirely. */
    @Value("${cms.security.allow-dev-identity-headers:false}")
    private boolean allowDevIdentityHeaders;

    @Around("@annotation(aaRoleGuard)")
    public Object checkRole(ProceedingJoinPoint joinPoint, AaRoleGuard aaRoleGuard) throws Throwable {
        String[] allowedRoles = aaRoleGuard.roles();

        Set<String> userRoles = extractUserRoles();

        if (userRoles.isEmpty()) {
            log.warn("AA access denied: no roles present on the request for {}",
                    joinPoint.getSignature().toShortString());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: insufficient role permissions for this AA action");
        }

        for (String allowedRole : allowedRoles) {
            if (userRoles.contains(allowedRole)) {
                return joinPoint.proceed();
            }
        }

        log.warn("Access denied: user roles {} do not include any of {}", userRoles, Arrays.toString(allowedRoles));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Access denied: insufficient role permissions for this AA action");
    }

    private Set<String> extractUserRoles() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return Collections.emptySet();

        HttpServletRequest request = attrs.getRequest();
        Set<String> roles = new HashSet<>();

        // The JWT is authoritative and is read FIRST.
        //
        // This previously checked X-User-Roles / X-User-Role ahead of the token and returned
        // immediately, so the JWT was never decoded: any caller who could set that header
        // self-asserted arbitrary AA roles, and a token carrying only RBIO_OFFICER was irrelevant if
        // the header said AA_DO. cms-backend is reached directly by both browsers (the gateway has no
        // route to it, see SecurityConfig), so there is no trusted upstream hop to strip those
        // headers. They are now honoured only under dev-local, where the E2E suites rely on them.
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                    @SuppressWarnings("unchecked")
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

        if (!roles.isEmpty() || !allowDevIdentityHeaders) {
            return roles;
        }

        // Dev-local only. The E2E suites drive this API with X-User-Roles via identityHeadersFor().
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
}
