package com.hrms.cms.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the authority check that replaces in-application user administration.
 *
 * <h2>Why every test here is about a REFUSAL</h2>
 * User administration is delegated to the SSO, so this aspect is the only thing standing between a token
 * and a capability. The dangerous outcomes are all "admitted when it should not have been", and none of
 * them throw — they proceed. So the central assertion in most of these tests is
 * {@code verify(joinPoint, never()).proceed()}: checking only that an exception was thrown would pass even
 * if the method had ALSO run.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequiresAuthorityAspectTest {

    @Mock private CmsPrincipalResolver principalResolver;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private Signature signature;

    private RequiresAuthorityAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new RequiresAuthorityAspect(principalResolver);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("SomeController.someMethod()");
    }

    /** A principal holding exactly the named authorities. */
    private void callerHolding(CmsAuthority... held) {
        Set<String> authorities = java.util.Arrays.stream(held)
                .map(CmsAuthority::authority)
                .collect(java.util.stream.Collectors.toSet());
        when(principalResolver.resolve()).thenReturn(new CmsPrincipalResolver.Principal(
                "rbio_reviewer_001", null, authorities, CmsPrincipalResolver.PrincipalType.RBI));
    }

    private void anonymousCaller() {
        when(principalResolver.resolve()).thenReturn(new CmsPrincipalResolver.Principal(
                null, null, Set.of(), CmsPrincipalResolver.PrincipalType.UNKNOWN));
    }

    private void regulatedEntityCallerHolding(CmsAuthority... held) {
        Set<String> authorities = java.util.Arrays.stream(held)
                .map(CmsAuthority::authority)
                .collect(java.util.stream.Collectors.toSet());
        when(principalResolver.resolve()).thenReturn(new CmsPrincipalResolver.Principal(
                "re_nodal_001", "HDFC", authorities, CmsPrincipalResolver.PrincipalType.REGULATED_ENTITY));
    }

    /** Builds the annotation, since it cannot be instantiated directly. */
    private RequiresAuthority annotation(boolean entityScope, CmsAuthority... required) {
        return new RequiresAuthority() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequiresAuthority.class;
            }
            @Override public CmsAuthority[] value() { return required; }
            @Override public boolean requiresEntityScope() { return entityScope; }
        };
    }

    @Nested
    @DisplayName("Refuses when the authority is not granted")
    class Refusals {

        @Test
        void anAnonymousCallerIsRefusedAndTheMethodNeverRuns() throws Throwable {
            anonymousCaller();

            assertThatThrownBy(() -> aspect.enforce(joinPoint,
                    annotation(false, CmsAuthority.MASTER_DATA_WRITE)))
                    .isInstanceOf(ResponseStatusException.class)
                    // Asserting the SPECIFIC message, not merely that something was thrown. Mutation
                    // testing showed the weaker assertion passed for the wrong reason: an anonymous caller
                    // holds no authorities, so deleting the identity check still produced a refusal from the
                    // later "authority not held" branch. The test looked like it guarded the identity check
                    // and guarded nothing. This wording only appears on that branch.
                    .hasMessageContaining("could not be identified");

            // The method must not have executed. An unidentified caller reaching a list endpoint serves
            // every record to somebody who never signed in.
            verify(joinPoint, never()).proceed();
        }

        @Test
        void aCallerWithoutTheAuthorityIsRefused() throws Throwable {
            callerHolding(CmsAuthority.RBIO_COMPLAINT_READ);

            assertThatThrownBy(() -> aspect.enforce(joinPoint,
                    annotation(false, CmsAuthority.RBIO_COMPLAINT_CLOSE_FINAL)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("RBIO_COMPLAINT_CLOSE_FINAL");

            verify(joinPoint, never()).proceed();
        }

        @Test
        void theRefusalIs403AndNot401() throws Throwable {
            // The token was accepted; what is missing is a GRANT. A 401 would tell the client to sign in
            // again, which cannot help, and would disguise a missing SSO mapping as a login problem.
            callerHolding(CmsAuthority.RBIO_COMPLAINT_READ);

            assertThatThrownBy(() -> aspect.enforce(joinPoint,
                    annotation(false, CmsAuthority.MASTER_DATA_WRITE)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void anEmptyAuthorityListRefusesEveryoneRatherThanAdmittingThem() throws Throwable {
            // The critical inversion. Read as "no requirement", an empty list would make an endpoint the
            // author believed was guarded completely open. Treated as a configuration error instead.
            callerHolding(CmsAuthority.MASTER_DATA_WRITE, CmsAuthority.REPORT_EXPORT);

            assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation(false)))
                    .isInstanceOf(ResponseStatusException.class);

            verify(joinPoint, never()).proceed();
        }

        @Test
        void holdingADifferentAuthorityDoesNotSubstitute() throws Throwable {
            // Guards against any "close enough" matching creeping in. MASTER_DATA_READ must never satisfy
            // MASTER_DATA_WRITE, however similar the names.
            callerHolding(CmsAuthority.MASTER_DATA_READ);

            assertThatThrownBy(() -> aspect.enforce(joinPoint,
                    annotation(false, CmsAuthority.MASTER_DATA_WRITE)))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Nested
    @DisplayName("Admits when the authority is granted")
    class Admissions {

        @Test
        void theExactAuthorityAdmits() throws Throwable {
            callerHolding(CmsAuthority.MASTER_DATA_WRITE);
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = aspect.enforce(joinPoint, annotation(false, CmsAuthority.MASTER_DATA_WRITE));

            assertThat(result).isEqualTo("ok");
            verify(joinPoint).proceed();
        }

        @Test
        void anyOneOfSeveralNamedAuthoritiesAdmits() throws Throwable {
            // The annotation is a disjunction, so an endpoint can accept either of two capabilities without
            // the caller needing both.
            callerHolding(CmsAuthority.REPORT_EXPORT);
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = aspect.enforce(joinPoint,
                    annotation(false, CmsAuthority.REPORT_VIEW, CmsAuthority.REPORT_EXPORT));

            assertThat(result).isEqualTo("ok");
        }
    }

    @Nested
    @DisplayName("Regulated Entity scope (both user types arrive from the same SSO)")
    class EntityScope {

        @Test
        void anRbiStaffCallerIsRefusedAnEntityScopedAction() throws Throwable {
            // RBI staff and RE users share one SSO, so a mapping mistake could grant an RE authority to RBI
            // staff. Without this check that staff member could file the ENTITY'S OWN reply to a complaint,
            // and the record would not show that the reply did not come from the entity.
            callerHolding(CmsAuthority.RE_COMPLAINT_RESPOND);

            assertThatThrownBy(() -> aspect.enforce(joinPoint,
                    annotation(true, CmsAuthority.RE_COMPLAINT_RESPOND)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("regulated entity");

            verify(joinPoint, never()).proceed();
        }

        @Test
        void aRegulatedEntityCallerWithTheAuthorityIsAdmitted() throws Throwable {
            regulatedEntityCallerHolding(CmsAuthority.RE_COMPLAINT_RESPOND);
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = aspect.enforce(joinPoint,
                    annotation(true, CmsAuthority.RE_COMPLAINT_RESPOND));

            assertThat(result).isEqualTo("ok");
        }

        @Test
        void anEntityCallerStillNeedsTheAuthority() throws Throwable {
            // Being an entity user is not itself a permission. Carrying entity_code must not admit a caller
            // to every RE endpoint.
            regulatedEntityCallerHolding(CmsAuthority.RE_COMPLAINT_READ);

            assertThatThrownBy(() -> aspect.enforce(joinPoint,
                    annotation(true, CmsAuthority.RE_COMPLAINT_RESPOND)))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Nested
    @DisplayName("The published catalogue is the SSO contract")
    class Catalogue {

        @Test
        void everyAuthorityIsPublishedWithADescriptionAndPrincipalType() {
            var catalogue = CmsAuthority.catalogue();

            assertThat(catalogue).hasSize(CmsAuthority.values().length);
            for (var item : catalogue) {
                assertThat((String) item.get("authority")).isNotBlank();
                // A blank description leaves an administrator guessing what they are granting.
                assertThat((String) item.get("description")).isNotBlank();
                assertThat((String) item.get("principalType")).isIn("RBI", "REGULATED_ENTITY");
            }
        }

        @Test
        void authorityNamesCarryNoRolePrefix() {
            // ROLE_ is Spring's convention for roles, and these are deliberately not roles. A prefixed name
            // would invite someone to map it as a realm role and wonder why nothing was granted.
            for (CmsAuthority a : CmsAuthority.values()) {
                assertThat(a.authority()).doesNotStartWith("ROLE_");
            }
        }

        @Test
        void regulatedEntityAuthoritiesAreIdentifiableByName() {
            assertThat(CmsAuthority.RE_COMPLAINT_RESPOND.isRegulatedEntityAuthority()).isTrue();
            assertThat(CmsAuthority.MASTER_DATA_WRITE.isRegulatedEntityAuthority()).isFalse();
        }

        @Test
        void parsingIsCaseInsensitiveButRejectsUnknownNames() {
            assertThat(CmsAuthority.from("master_data_write")).contains(CmsAuthority.MASTER_DATA_WRITE);
            assertThat(CmsAuthority.from("  MASTER_DATA_WRITE  ")).contains(CmsAuthority.MASTER_DATA_WRITE);
            // An unknown name must not resolve to anything. Silently mapping it to a real authority would
            // turn an SSO typo into a grant.
            assertThat(CmsAuthority.from("MASTER_DATA_WRITE_ALL")).isEmpty();
            assertThat(CmsAuthority.from("")).isEmpty();
            assertThat(CmsAuthority.from(null)).isEmpty();
        }
    }
}
