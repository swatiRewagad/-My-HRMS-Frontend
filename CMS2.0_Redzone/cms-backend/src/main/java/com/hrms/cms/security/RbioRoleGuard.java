package com.hrms.cms.security;

import java.lang.annotation.*;

/**
 * Annotation to restrict RBIO endpoint access to specific Keycloak roles.
 * Applied to controller methods; enforced by {@link RbioRoleGuardAspect}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RbioRoleGuard {

    /**
     * Allowed roles (e.g., "RBIO_OFFICER", "RBIO_ADMIN").
     * The user must have at least one of these roles.
     */
    String[] roles();
}
