package com.hrms.cms.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the caller to hold one of the named authorities, as granted by the SSO.
 *
 * <p>Use this in preference to the role guards. A role guard hardcodes an authorisation decision — adding
 * an RBIO rank means editing and redeploying every annotation that should admit it. An authority names the
 * capability and lets the SSO decide which roles carry it, so the same change becomes a mapping update
 * with no release.
 *
 * <p>METHOD target only, and deliberately so: the existing role-guard aspects declare
 * {@code @Target({METHOD, TYPE})} but pointcut only on {@code @annotation(...)}, so a class-level
 * placement is silently unenforced. Allowing only what is actually checked means a guard cannot be put
 * somewhere it will not run.
 *
 * <p>Note {@code @PreAuthorize} is NOT an option in this application: {@code @EnableMethodSecurity} is
 * absent, so all 31 existing {@code @PreAuthorize} annotations are inert. This aspect runs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAuthority {

    /** Any one of these admits the caller. Empty means nobody, which fails closed by construction. */
    CmsAuthority[] value();

    /**
     * Whether the caller must be a Regulated Entity principal (i.e. carry an {@code entity_code}).
     *
     * <p>Set on endpoints that act on behalf of a specific entity. Without it, an RBI staff member who was
     * granted an RE authority by a mapping mistake could submit an entity's own response, and there would
     * be no record that the reply did not come from the entity.
     */
    boolean requiresEntityScope() default false;
}
