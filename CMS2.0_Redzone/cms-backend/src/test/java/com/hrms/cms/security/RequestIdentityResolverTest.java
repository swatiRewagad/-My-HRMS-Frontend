package com.hrms.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequestIdentityResolver}.
 *
 * <h2>Why the default here is the ENFORCING configuration</h2>
 *
 * <p>This resolver decides WHO the caller is for two security-critical consumers:
 * {@code RevocationCheckFilter} (which refuses a revoked user) and the {@code reveal-pii} endpoint
 * (which returns a citizen's real name, phone, email and account number). An earlier revision read
 * {@code X-User-Id} and {@code X-User-Roles} <em>ahead</em> of the bearer token and returned
 * immediately, with no {@code cms.security.allow-dev-identity-headers} gate of any kind — so in
 * every profile, production included, one header defeated revocation and another minted a
 * PII-reveal-capable role.
 *
 * <p>cms-backend is reached directly by browsers (the gateway has no route to it), so there is no
 * trusted upstream hop stripping client-supplied identity headers. The token must therefore win.
 *
 * <p>Constructing the resolver with {@code new} leaves {@code allowDevIdentityHeaders} false, i.e.
 * the enforcing profile, unless a test explicitly calls {@link #enableDevHeaders()}.
 */
class RequestIdentityResolverTest {

    private RequestIdentityResolver resolver;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        resolver = new RequestIdentityResolver(new ObjectMapper());
        request = new MockHttpServletRequest();
    }

    private void enableDevHeaders() {
        ReflectionTestUtils.setField(resolver, "allowDevIdentityHeaders", true);
    }

    @Nested
    @DisplayName("enforcing profile (dev identity headers disabled)")
    class Enforcing {

        @Test
        @DisplayName("X-User-Id cannot override the token's subject — revocation bypass regression")
        void headerCannotImpersonateAnotherUserId() {
            // The revocation filter looks up isRevoked(identity.getUserId()). If the header won, a
            // revoked user would simply name somebody else and sail through.
            request.addHeader("Authorization", bearerWith("{\"preferred_username\":\"revoked_user\"}"));
            request.addHeader("X-User-Id", "someone_else");

            RequestIdentity identity = resolver.resolve(request);

            assertThat(identity).isNotNull();
            assertThat(identity.getUserId()).isEqualTo("revoked_user");
        }

        @Test
        @DisplayName("X-User-Roles cannot mint a role the token does not carry — PII reveal regression")
        void headerCannotEscalateRolesBeyondToken() {
            // AA_DO is deliberately absent from PiiMaskingService.DEFAULT_REVEAL_ROLES; RBIO_OFFICER
            // is present. Header-first extraction let an AA_DO simply declare itself RBIO_OFFICER.
            request.addHeader("Authorization", bearerWithRealmRoles("AA_DO"));
            request.addHeader("X-User-Roles", "RBIO_OFFICER");

            RequestIdentity identity = resolver.resolve(request);

            assertThat(identity).isNotNull();
            assertThat(identity.getRoles()).containsExactly("AA_DO");
            assertThat(identity.getRoles()).doesNotContain("RBIO_OFFICER");
        }

        @Test
        @DisplayName("X-User-Role (single-role variant) is equally ignored")
        void singleRoleHeaderIsIgnored() {
            request.addHeader("Authorization", bearerWithRealmRoles("AA_DO"));
            request.addHeader("X-User-Role", "ADMIN");

            RequestIdentity identity = resolver.resolve(request);

            assertThat(identity.getRoles()).containsExactly("AA_DO");
        }

        @Test
        @DisplayName("headers alone establish no identity at all")
        void headersAloneYieldNoIdentity() {
            request.addHeader("X-User-Id", "forged_user");
            request.addHeader("X-User-Roles", "ADMIN");

            assertThat(resolver.resolve(request)).isNull();
        }

        @Test
        @DisplayName("X-Entity-Code cannot point an RE caller at another bank's data")
        void entityCodeHeaderIsIgnored() {
            request.addHeader("Authorization", bearerWithRealmRolesAndClaims(
                    "{\"preferred_username\":\"re_pno_001\",\"entity_code\":\"HDFC Bank\"}", "RE_PNO"));
            request.addHeader("X-Entity-Code", "State Bank of India");

            RequestIdentity identity = resolver.resolve(request);

            assertThat(identity.getSide()).isEqualTo("RE");
            assertThat(identity.getEntityCode()).isEqualTo("HDFC Bank");
        }

        @Test
        @DisplayName("an RE caller whose token carries no entity_code gets none from a header")
        void entityCodeIsNotInventedFromHeader() {
            request.addHeader("Authorization", bearerWithRealmRoles("RE_PNO"));
            request.addHeader("X-Entity-Code", "State Bank of India");

            RequestIdentity identity = resolver.resolve(request);

            assertThat(identity.getSide()).isEqualTo("RE");
            assertThat(identity.getEntityCode()).isNull();
        }

        @Test
        @DisplayName("the token's subject and roles are resolved when no header is present")
        void tokenIsResolvedNormally() {
            request.addHeader("Authorization", bearerWithRealmRolesAndClaims(
                    "{\"preferred_username\":\"aa_do_001\",\"name\":\"AA Dealing Officer\"}", "AA_DO"));

            RequestIdentity identity = resolver.resolve(request);

            assertThat(identity.getUserId()).isEqualTo("aa_do_001");
            assertThat(identity.getDisplayName()).isEqualTo("AA Dealing Officer");
            assertThat(identity.getRoles()).containsExactly("AA_DO");
            assertThat(identity.getSide()).isEqualTo("RBI");
        }
    }

    @Nested
    @DisplayName("dev-local profile (dev identity headers enabled)")
    class DevLocal {

        @Test
        @DisplayName("headers establish an identity when no token is present, so the E2E suites work")
        void headersAreHonouredWithoutAToken() {
            enableDevHeaders();
            request.addHeader("X-User-Id", "aa_do_001");
            request.addHeader("X-User-Roles", "AA_DO");

            RequestIdentity identity = resolver.resolve(request);

            assertThat(identity).isNotNull();
            assertThat(identity.getUserId()).isEqualTo("aa_do_001");
            assertThat(identity.getRoles()).containsExactly("AA_DO");
        }

        @Test
        @DisplayName("even in dev-local the token still beats a conflicting header")
        void tokenStillWinsInDevLocal() {
            enableDevHeaders();
            request.addHeader("Authorization", bearerWithRealmRolesAndClaims(
                    "{\"preferred_username\":\"real_user\"}", "AA_DO"));
            request.addHeader("X-User-Id", "someone_else");
            request.addHeader("X-User-Roles", "AA_ADMIN");

            RequestIdentity identity = resolver.resolve(request);

            assertThat(identity.getUserId()).isEqualTo("real_user");
            assertThat(identity.getRoles()).containsExactly("AA_DO");
        }

        @Test
        @DisplayName("a token carrying no role claims legitimately falls back to the header")
        void roleHeaderFallbackAppliesWhenTokenHasNoRoles() {
            enableDevHeaders();
            request.addHeader("Authorization", bearerWith("{\"sub\":\"cepc_do1\"}"));
            request.addHeader("X-User-Roles", "CEPC_DO");

            RequestIdentity identity = resolver.resolve(request);

            assertThat(identity.getUserId()).isEqualTo("cepc_do1");
            assertThat(identity.getRoles()).containsExactly("CEPC_DO");
        }
    }

    // ---------------------------------------------------------------------

    private static String unsignedJwt(String jsonPayload) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".not-a-real-signature";
    }

    private static String bearerWith(String jsonPayload) {
        return "Bearer " + unsignedJwt(jsonPayload);
    }

    private static String bearerWithRealmRoles(String... roles) {
        return bearerWithRealmRolesAndClaims("{\"preferred_username\":\"token_user\"}", roles);
    }

    /** Merges the given claim JSON (without its closing brace) with a realm_access role list. */
    private static String bearerWithRealmRolesAndClaims(String claimJson, String... roles) {
        StringBuilder json = new StringBuilder(claimJson.substring(0, claimJson.length() - 1));
        json.append(",\"realm_access\":{\"roles\":[");
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) json.append(',');
            json.append('"').append(roles[i]).append('"');
        }
        json.append("]}}");
        return "Bearer " + unsignedJwt(json.toString());
    }
}
