package com.hrms.cms.support;

import com.hrms.cms.config.AntiAutomationFilter;
import com.hrms.cms.config.CorsConfig;
import com.hrms.cms.config.PiiDecryptionFilter;
import com.hrms.cms.config.RateLimitFilter;
import com.hrms.cms.config.RevocationCheckFilter;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@link WebMvcTest} slice that excludes the application's servlet filters.
 *
 * {@code @WebMvcTest} deliberately includes {@code jakarta.servlet.Filter} beans, since filters are
 * part of the web layer. That is unhelpful here: this application's filters carry heavyweight
 * collaborators ({@code EncryptionKeyService}, {@code CredentialRevocationService}) which are not
 * part of a controller slice, and a single missing bean fails the whole ApplicationContext — so
 * every test method in the class errors for a reason unrelated to the controller under test.
 *
 * Excluding them centrally means adding a new filter cannot silently break every controller test,
 * which is exactly what happened when RevocationCheckFilter was introduced.
 *
 * Note {@code @AutoConfigureMockMvc(addFilters = false)} does NOT solve this: it stops filters being
 * applied to the request, not being instantiated during the scan.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@WebMvcTest(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
                PiiDecryptionFilter.class,
                RateLimitFilter.class,
                AntiAutomationFilter.class,
                RevocationCheckFilter.class,
                CorsConfig.class
        }))
public @interface ControllerSliceTest {

    /** The controllers under test, mirroring {@code @WebMvcTest(controllers = ...)}. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
