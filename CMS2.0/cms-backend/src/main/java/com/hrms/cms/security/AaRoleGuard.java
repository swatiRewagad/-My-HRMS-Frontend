package com.hrms.cms.security;

import java.lang.annotation.*;

/**
 * Annotation to restrict AA (Appellate Authority) endpoint access to specific Keycloak roles.
 * Applied to controller methods; enforced by {@link AaRoleGuardAspect}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AaRoleGuard {

    /**
     * Allowed roles (e.g., "AA_REGISTRAR", "AA_AUTHORITY").
     * The user must have at least one of these roles.
     */
    String[] roles();
}
