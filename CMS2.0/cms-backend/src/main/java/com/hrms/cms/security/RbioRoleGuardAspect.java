package com.hrms.cms.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;

/**
 * AOP aspect that enforces role-based access control on RBIO endpoints.
 * Checks the current user's roles from the JWT token (passed via request headers)
 * against the allowed roles specified in {@link RbioRoleGuard}.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RbioRoleGuardAspect {

    private final CallerIdentity callerIdentity;

    @Around("@annotation(rbioRoleGuard)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RbioRoleGuard rbioRoleGuard) throws Throwable {
        String[] allowedRoles = rbioRoleGuard.roles();

        Set<String> userRoles = callerIdentity.roles();

        if (userRoles.isEmpty()) {
            // If no roles can be extracted (e.g., dev mode, no token), allow through
            log.debug("No roles found in request - allowing through (dev mode or missing token)");
            return joinPoint.proceed();
        }

        for (String allowedRole : allowedRoles) {
            if (userRoles.contains(allowedRole)) {
                return joinPoint.proceed();
            }
        }

        log.warn("Access denied: user roles {} do not include any of {}", userRoles, Arrays.toString(allowedRoles));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Access denied: insufficient role permissions for this RBIO action");
    }
}
