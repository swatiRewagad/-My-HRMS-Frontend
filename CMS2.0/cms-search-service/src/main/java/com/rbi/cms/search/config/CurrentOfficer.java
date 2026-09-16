package com.rbi.cms.search.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated {@code OfficerPrincipal} into a controller method.
 *
 * <p>Preferred over {@code @AuthenticationPrincipal} because that injects null under the
 * {@code permitAll} chains this repo uses in local profiles, turning a missing principal into a
 * {@code NullPointerException} deep inside the query builder. The resolver behind this annotation
 * fails loudly instead.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentOfficer {
}
