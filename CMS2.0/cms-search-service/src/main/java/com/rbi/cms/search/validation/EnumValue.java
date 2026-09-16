package com.rbi.cms.search.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asserts that a string filter names a constant of the given enum, accepting either the constant
 * name ({@code NEW_COMPLAINT}) or its label ({@code "New Complaint"}).
 *
 * <p>Null and blank pass: every field this guards is an optional filter, and a cleared grid column
 * arrives as {@code ""}.
 *
 * <p>Preferred over a Jackson deserializer because a deserializer cannot report which field was
 * wrong — it surfaces as {@code HttpMessageNotReadableException} with no path. It also replaces the
 * silent-default behaviour of {@code ComplaintStatusDeserializer}, which mapped anything unknown to
 * {@code NEW} and so turned a typo into plausible-looking results for the wrong status.
 */
@Documented
@Constraint(validatedBy = EnumValueValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValue {

    Class<? extends Enum<?>> value();

    String message() default "must be one of {allowed}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
