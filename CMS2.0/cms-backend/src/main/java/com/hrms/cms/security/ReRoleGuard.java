package com.hrms.cms.security;

import java.lang.annotation.*;

/**
 * Annotation to restrict RE (Regulated Entity) portal endpoint access to specific roles.
 * Applied to controller methods or classes; enforced by {@link ReRoleGuardAspect}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReRoleGuard {

    /**
     * Allowed roles (e.g., "RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN").
     * The user must have at least one of these roles.
     */
    String[] roles();
}
