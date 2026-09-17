package com.hrms.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AaRoleGuardAspect}.
 *
 * <p>Deliberately NOT a {@code @WebMvcTest}: a plain {@code @Aspect}/{@code @Component} is not
 * instantiated in an MVC slice, so the guard is simply absent there and every request would pass
 * through it — an "expect 403" assertion would fail and an "expect 200" assertion would merely be
 * asserting the aspect's absence. The aspect reads the request via {@link RequestContextHolder},
 * so it can be driven directly with a {@link MockHttpServletRequest} and a mocked join point.
 *
 * <h2>Why the default here is the ENFORCING configuration — do not "helpfully" relax it</h2>
 *
 * <p>The aspect resolves roles from the {@code Authorization: Bearer} JWT <em>first</em>, and the
 * JWT is authoritative. The {@code X-User-Roles} / {@code X-User-Role} request headers are a
 * <strong>fallback only</strong>, consulted when BOTH (a) the token yielded no roles at all and
 * (b) {@code cms.security.allow-dev-identity-headers} is {@code true}. That property defaults to
 * {@code false} and is set to {@code true} only in {@code application-dev-local.yml}, where the
 * Playwright E2E suites drive the API with dev identity headers via {@code identityHeadersFor()}.
 *
 * <p>This matters because cms-backend is reached directly by browsers (the gateway has no route to
 * it), so there is no trusted upstream hop that strips client-supplied identity headers. An earlier
 * revision read those headers <em>ahead</em> of the token and returned immediately, so any caller
 * could self-assert an arbitrary AA role and the token was never decoded. That was a real privilege
 * escalation and {@link #headerCannotEscalatePrivilegeBeyondJwtRoles()} exists specifically to make
 * a regression of it fail loudly.
 *
 * <p>Because these tests construct the aspect with {@code new AaRoleGuardAspect(...)}, Spring never
 * injects {@code allowDevIdentityHeaders}, so it is {@code false} — i.e. every test below exercises
 * the production/enforcing configuration unless it explicitly calls {@link #enableDevHeaders()}.
 * If you find yourself needing to add {@code enableDevHeaders()} to make an unrelated test pass,
 * that test has an identity problem: give it a JWT, do not widen the escape hatch.
 */
class AaRoleGuardAspectTest {

    private static final String DENIAL_MESSAGE =
            "Access denied: insufficient role permissions for this AA action";

    private AaRoleGuardAspect aspect;
    private MockHttpServletRequest request;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() throws Throwable {
        // No Spring context: allowDevIdentityHeaders stays false, i.e. the enforcing profile.
        aspect = new AaRoleGuardAspect(new ObjectMapper());

        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("target-invoked");
        // The denial path logs joinPoint.getSignature().toShortString(); leaving it unstubbed
        // would NPE and mask the assertion under test.
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("AaController.someAaAction(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void tearDown() {
        // A leaked RequestContextHolder is thread-local and would corrupt sibling tests in this JVM.
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * Simulates the {@code dev-local} profile, where
     * {@code cms.security.allow-dev-identity-headers: true} lets the E2E suites authenticate with
     * {@code X-User-Roles} instead of a real Keycloak token. Only tests that are specifically about
     * that escape hatch may call this.
     */
    private void enableDevHeaders() {
        ReflectionTestUtils.setField(aspect, "allowDevIdentityHeaders", true);
    }

    // ---------------------------------------------------------------------
    // Annotation carriers. The aspect takes an AaRoleGuard instance as an argument,
    // so real annotation instances are read reflectively off these stubs.
    // ---------------------------------------------------------------------

    @AaRoleGuard(roles = {"AA_DO"})
    private void allowsAaDoOnly() {
    }

    @AaRoleGuard(roles = {"AA_REVIEWER"})
    private void allowsAaReviewerOnly() {
    }

    @AaRoleGuard(roles = {"AA_ADMIN"})
    private void allowsAaAdminOnly() {
    }

    @AaRoleGuard(roles = {"AA_SECRETARIAT", "AA_AUTHORITY"})
    private void allowsSecretariatOrAuthority() {
    }

    private AaRoleGuard guardOn(String methodName) throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod(methodName);
        AaRoleGuard guard = method.getAnnotation(AaRoleGuard.class);
        assertThat(guard).as("test fixture must carry @AaRoleGuard").isNotNull();
        return guard;
    }

    /**
     * Builds an unsigned, real-shaped JWT: {@code base64url(header).base64url(payload).sig}.
     *
     * <p>NOTE: the aspect only base64-decodes the payload segment — it does NOT verify the
     * signature, issuer or expiry. That is Spring Security's job upstream (resource-server JWT
     * validation). This helper existing does not mean the aspect validates tokens; a future reader
     * must not read these tests as evidence of token validation.
     */
    private static String unsignedJwt(String jsonPayload) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".not-a-real-signature";
    }

    private static String bearerWithRealmRoles(String... roles) {
        StringBuilder json = new StringBuilder("{\"realm_access\":{\"roles\":[");
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) json.append(',');
            json.append('"').append(roles[i]).append('"');
        }
        json.append("]}}");
        return "Bearer " + unsignedJwt(json.toString());
    }

    // =====================================================================
    // ENFORCING PROFILE (the default, and the shipped configuration):
    // X-User-Roles / X-User-Role carry no authority whatsoever.
    // =====================================================================

    @Test
    @DisplayName("ENFORCING: X-User-Roles alone, with no Bearer token, is denied with 403 and never proceeds")
    void deniesHeaderOnlyRoleWhenDevHeadersDisabled() throws Throwable {
        // Self-asserted identity. Without dev headers enabled the aspect resolves zero roles.
        request.addHeader("X-User-Roles", "AA_DO");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(DENIAL_MESSAGE)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("ENFORCING: single X-User-Role alone, with no Bearer token, is denied with 403")
    void deniesSingleRoleHeaderWhenDevHeadersDisabled() throws Throwable {
        request.addHeader("X-User-Role", "AA_DO");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    /**
     * THE VULNERABILITY REGRESSION TEST. Read this before changing role resolution order.
     *
     * <p>A caller holds a genuine token, but only for {@code AA_DO}. They attack an
     * {@code AA_ADMIN}-only endpoint by simply adding {@code X-User-Roles: AA_ADMIN} to the request
     * — a header any HTTP client can set, since cms-backend is called directly by browsers and
     * nothing upstream strips it.
     *
     * <p>The token must decide: this is a 403. If this test ever goes green on
     * {@code joinPoint.proceed()}, header-first role resolution has been reintroduced and arbitrary
     * privilege escalation to any AA role is live in production.
     */
    @Test
    @DisplayName("ENFORCING: X-User-Roles cannot ESCALATE past the token — AA_DO token + AA_ADMIN header on an AA_ADMIN endpoint is 403")
    void headerCannotEscalatePrivilegeBeyondJwtRoles() throws Throwable {
        request.addHeader("Authorization", bearerWithRealmRoles("AA_DO"));
        request.addHeader("X-User-Roles", "AA_ADMIN");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaAdminOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(DENIAL_MESSAGE)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("ENFORCING: the JWT wins over conflicting X-User-Roles — a token without the role is denied even when the header claims it")
    void jwtTakesPrecedenceOverHeaderClaims() throws Throwable {
        // Inverse of the pre-fix behaviour: the token (RBIO_OFFICER) is decoded first and, because it
        // yields roles, the AA_DO header is never consulted at all.
        request.addHeader("Authorization", bearerWithRealmRoles("RBIO_OFFICER"));
        request.addHeader("X-User-Roles", "AA_DO");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(DENIAL_MESSAGE)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    // ---------------------------------------------------------------------
    // A real AA role that is not the allowed one -> 403
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A real AA role in the token that is not in the allowed list is denied with 403")
    void deniesWhenRolePresentButNotAllowed() throws Throwable {
        request.addHeader("Authorization", bearerWithRealmRoles("AA_SECRETARIAT"));

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(DENIAL_MESSAGE)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Asserting only the exception would let a guard that throws AND proceeds pass.
        verify(joinPoint, never()).proceed();
    }

    // ---------------------------------------------------------------------
    // Roleless request -> 403 (must NOT fail open)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A request with no identity headers and no Authorization header is denied, not allowed through")
    void deniesRolelessRequest() throws Throwable {
        // No X-User-Roles, no X-User-Role, no Authorization: the classic fail-open shape.
        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(DENIAL_MESSAGE)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("No request bound to the thread at all is denied")
    void deniesWhenNoRequestContext() throws Throwable {
        RequestContextHolder.resetRequestAttributes();

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    // ---------------------------------------------------------------------
    // Roles from a real-shaped (unsigned) Keycloak JWT
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("realm_access.roles from a Bearer JWT payload are honoured")
    void allowsWhenRealmAccessRoleMatches() throws Throwable {
        request.addHeader("Authorization",
                "Bearer " + unsignedJwt("{\"realm_access\":{\"roles\":[\"AA_REVIEWER\"]}}"));

        Object result = aspect.checkRole(joinPoint, guardOn("allowsAaReviewerOnly"));

        assertThat(result).isEqualTo("target-invoked");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("resource_access.<client>.roles from a Bearer JWT payload are also honoured")
    void allowsWhenResourceAccessRoleMatches() throws Throwable {
        request.addHeader("Authorization", "Bearer " + unsignedJwt(
                "{\"resource_access\":{\"cms-portal\":{\"roles\":[\"AA_AUTHORITY\"]}}}"));

        Object result = aspect.checkRole(joinPoint, guardOn("allowsSecretariatOrAuthority"));

        assertThat(result).isEqualTo("target-invoked");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("A JWT carrying only unrelated roles is denied")
    void deniesWhenJwtRolesDoNotMatch() throws Throwable {
        request.addHeader("Authorization", "Bearer " + unsignedJwt(
                "{\"realm_access\":{\"roles\":[\"RBIO_OFFICER\",\"offline_access\"]}}"));

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaReviewerOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    // ---------------------------------------------------------------------
    // Malformed Authorization header -> 403, not 500
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A malformed Bearer token yields 403, never a 500 that would mask the denial")
    void deniesMalformedBearerToken() throws Throwable {
        request.addHeader("Authorization", "Bearer notajwt");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(DENIAL_MESSAGE)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("A Bearer token whose payload is not valid base64/JSON yields 403, not 500")
    void deniesBearerTokenWithUndecodablePayload() throws Throwable {
        request.addHeader("Authorization", "Bearer aaa.!!!not-base64!!!.ccc");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("A non-Bearer Authorization scheme is ignored and the request is denied")
    void deniesNonBearerAuthorizationHeader() throws Throwable {
        request.addHeader("Authorization", "Basic QUFfRE86c2VjcmV0");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    // ---------------------------------------------------------------------
    // Case sensitivity — ACTUAL behaviour, not preferred behaviour
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Role matching is case-sensitive: 'aa_do' does NOT satisfy roles={\"AA_DO\"}")
    void roleMatchingIsCaseSensitive() throws Throwable {
        // Pinning the code as written: extractUserRoles() puts claim values verbatim into a
        // HashSet and the guard uses Set.contains(allowedRole), so comparison is exact-match.
        // This is documented behaviour of the current implementation, not an endorsement.
        request.addHeader("Authorization", bearerWithRealmRoles("aa_do"));

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("Role matching does not strip a ROLE_ prefix: 'ROLE_AA_DO' does NOT satisfy roles={\"AA_DO\"}")
    void roleMatchingDoesNotStripRolePrefix() throws Throwable {
        request.addHeader("Authorization", bearerWithRealmRoles("ROLE_AA_DO"));

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("An empty roles={} annotation denies every caller, however privileged")
    void emptyAllowedRolesDeniesEveryone() throws Throwable {
        request.addHeader("Authorization", bearerWithRealmRoles("AA_DO", "AA_AUTHORITY", "AA_ADMIN"));

        AaRoleGuard emptyGuard = mock(AaRoleGuard.class);
        when(emptyGuard.roles()).thenReturn(new String[0]);

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, emptyGuard))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    // ---------------------------------------------------------------------
    // Pass-through semantics of the proceed path.
    // Authenticated with a JWT so these hold in BOTH profiles.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Exceptions thrown by the target are propagated unchanged, not converted to 403")
    void propagatesTargetException() throws Throwable {
        request.addHeader("Authorization", bearerWithRealmRoles("AA_DO"));
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("A null return from the target is passed through as null, not swallowed")
    void passesThroughNullReturn() throws Throwable {
        request.addHeader("Authorization", bearerWithRealmRoles("AA_DO"));
        when(joinPoint.proceed()).thenReturn(null);

        assertThat(aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly"))).isNull();
        verify(joinPoint).proceed();
    }

    // =====================================================================
    // DEV-LOCAL PROFILE (cms.security.allow-dev-identity-headers=true).
    // Exists only so the Playwright E2E suites can authenticate without Keycloak.
    // Even here the header is a FALLBACK, never an override.
    // =====================================================================

    @Test
    @DisplayName("DEV-LOCAL: X-User-Roles containing the allowed role proceeds and returns the target's value")
    void devHeadersAllowWhenHeaderRoleMatches() throws Throwable {
        enableDevHeaders();
        request.addHeader("X-User-Roles", "AA_DO");

        Object result = aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly"));

        assertThat(result).isEqualTo("target-invoked");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("DEV-LOCAL: comma-separated roles are all considered, including whitespace-padded ones")
    void devHeadersAllowSecondRoleInCommaList() throws Throwable {
        enableDevHeaders();
        request.addHeader("X-User-Roles", "RBIO_OFFICER, AA_DO");

        Object result = aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly"));

        assertThat(result).isEqualTo("target-invoked");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("DEV-LOCAL: single X-User-Role header is honoured when X-User-Roles is absent")
    void devHeadersAllowSingleRoleHeader() throws Throwable {
        enableDevHeaders();
        request.addHeader("X-User-Role", "AA_DO");

        Object result = aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly"));

        assertThat(result).isEqualTo("target-invoked");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("DEV-LOCAL: a blank X-User-Roles header is not treated as an identity")
    void devHeadersDenyBlankRolesHeader() throws Throwable {
        enableDevHeaders();
        request.addHeader("X-User-Roles", "   ");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("DEV-LOCAL: a real AA role in the header that is not in the allowed list is still denied")
    void devHeadersDenyRolePresentButNotAllowed() throws Throwable {
        enableDevHeaders();
        request.addHeader("X-User-Roles", "AA_SECRETARIAT");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(DENIAL_MESSAGE)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("DEV-LOCAL: a JWT that yields roles still wins over a conflicting header — no escalation even here")
    void devHeadersJwtRolesStillWinOverConflictingHeader() throws Throwable {
        enableDevHeaders();
        // The token yields RBIO_OFFICER, so the header fallback is never reached: the AA_DO header
        // is ignored and the caller is denied. Enabling dev headers must not open the escalation.
        request.addHeader("Authorization", bearerWithRealmRoles("RBIO_OFFICER"));
        request.addHeader("X-User-Roles", "AA_DO");

        assertThatThrownBy(() -> aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(DENIAL_MESSAGE)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("DEV-LOCAL: the header is used only when the token yields no roles at all")
    void devHeadersFallBackToHeaderOnlyWhenJwtYieldsNoRoles() throws Throwable {
        enableDevHeaders();
        // A token present but carrying no role claims => the fallback legitimately applies.
        request.addHeader("Authorization", "Bearer " + unsignedJwt("{\"sub\":\"cepc_do1\"}"));
        request.addHeader("X-User-Roles", "AA_DO");

        Object result = aspect.checkRole(joinPoint, guardOn("allowsAaDoOnly"));

        assertThat(result).isEqualTo("target-invoked");
        verify(joinPoint).proceed();
    }
}
