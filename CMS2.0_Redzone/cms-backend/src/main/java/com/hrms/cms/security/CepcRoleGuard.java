package com.hrms.cms.security;

import java.lang.annotation.*;

/**
 * Annotation to restrict CEPC endpoint access to specific Keycloak roles.
 * Applied to controller methods; enforced by {@link CepcRoleGuardAspect}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CepcRoleGuard {

    /**
     * Allowed roles (e.g., "CEPC_DO", "CEPC_ADMIN").
     * The user must have at least one of these roles.
     */
    String[] roles();
}
