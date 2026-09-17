package com.hrms.cms.config;

import com.hrms.cms.security.KeycloakJwtAuthConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B1 — proves the production filter chain actually enforces authorization.
 *
 * A slice test with stub controllers rather than a full context: the aim is to exercise
 * {@link SecurityConfig}'s request matchers in isolation, and booting the whole application would
 * drag in Oracle/MySQL, Kafka and Hazelcast without testing anything more about the rules. No H2 —
 * this project does not use it.
 *
 * Mutation-verified: replacing SecurityConfig's rules with anyRequest().permitAll() turns every
 * 401/403 expectation below into a 200, and the suite fails.
 */
@WebMvcTest(controllers = SecurityConfigEnforcementTest.StubEndpoints.class,
        // The application's @Component servlet filters are excluded: they pull in encryption,
        // rate-limiting and revocation dependencies that have nothing to do with the request
        // matchers under test here.
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {PiiDecryptionFilter.class, RateLimitFilter.class,
                           AntiAutomationFilter.class, RevocationCheckFilter.class, CorsConfig.class}))
@Import({SecurityConfig.class, KeycloakJwtAuthConverter.class,
         SecurityConfigEnforcementTest.StubEndpoints.class})
class SecurityConfigEnforcementTest {

    /**
     * Stand-ins for the real controllers, mapped to the paths SecurityConfig protects. Using stubs
     * keeps the assertions about the security rules rather than about controller behaviour.
     */
    @RestController
    static class StubEndpoints {
        @GetMapping("/api/v1/workflow/rbio/tasks")
        String rbioTasks() {
            return "rbio";
        }

        @GetMapping("/api/v1/workflow/cepc/tasks")
        String cepcTasks() {
            return "cepc";
        }

        @GetMapping("/api/v1/admin/security/alerts")
        String adminAlerts() {
            return "alerts";
        }

        @GetMapping("/api/v1/keycloak/users/all")
        String keycloakUsers() {
            return "users";
        }

        @GetMapping("/api/v1/citizen/auth/ping")
        String citizenPing() {
            return "citizen";
        }

        @GetMapping("/api/v1/i18n/translations/en")
        String translations() {
            return "i18n";
        }

        @GetMapping("/api/v1/complaints/recent")
        String recent() {
            return "recent";
        }

        @GetMapping("/api/complaints/track/ABC")
        String track() {
            return "track";
        }

        @GetMapping("/api/banks")
        String banks() {
            return "banks";
        }

        @GetMapping("/api/categories")
        String categories() {
            return "categories";
        }
    }

    /** Required because the chain enables oauth2ResourceServer; no live Keycloak is contacted. */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    // ───────────────────── Unauthenticated callers are refused ─────────────────────

    @Test
    void staffEndpointRejectsAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/v1/workflow/rbio/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRejectsAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keycloakUserApiRejectsAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/v1/keycloak/users/all"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * cms-backend is called directly by the browsers, with no gateway in front, so client-supplied
     * identity headers must carry no authority here. If this ever passes, anyone can become an
     * administrator with a curl header.
     */
    @Test
    void devIdentityHeadersAloneGrantNothing() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security/alerts")
                        .header("X-User-Id", "attacker")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    // ───────────────────── Authenticated but insufficient role ─────────────────────

    @Test
    void adminEndpointRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security/alerts").with(jwtWithRole("RBIO_OFFICER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rbioEndpointRejectsCepcOnlyRole() throws Exception {
        mockMvc.perform(get("/api/v1/workflow/rbio/tasks").with(jwtWithRole("CEPC_DO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cepcEndpointRejectsRbioOnlyRole() throws Exception {
        mockMvc.perform(get("/api/v1/workflow/cepc/tasks").with(jwtWithRole("RBIO_OFFICER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserWithNoRolesIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/workflow/rbio/tasks").with(jwt()))
                .andExpect(status().isForbidden());
    }

    /** There is no SECURITY_ADMIN role in the realm; inventing one must grant nothing. */
    @Test
    void inventedSecurityAdminRoleGrantsNothing() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security/alerts").with(jwtWithRole("SECURITY_ADMIN")))
                .andExpect(status().isForbidden());
    }

    // ───────────────────── Correct role is admitted ─────────────────────

    @Test
    void adminRoleReachesAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security/alerts").with(jwtWithRole("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void rbioOfficerReachesRbioEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/workflow/rbio/tasks").with(jwtWithRole("RBIO_OFFICER")))
                .andExpect(status().isOk());
    }

    // ───────────────────── Citizen paths must stay open ─────────────────────

    @Test
    void citizenAuthRemainsAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/citizen/auth/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void translationsRemainAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/i18n/translations/en"))
                .andExpect(status().isOk());
    }

    /** Anonymous but masked; PiiMaskingService protects the content, not the chain. */
    @Test
    void recentComplaintsRemainAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/complaints/recent"))
                .andExpect(status().isOk());
    }

    @Test
    void legacyPublicTrackerRemainsAnonymous() throws Exception {
        mockMvc.perform(get("/api/complaints/track/ABC"))
                .andExpect(status().isOk());
    }

    /**
     * Reference data feeds the public complaint form, so locking it down would break filing for
     * anyone not signed in. Guards against the enforcing chain overreaching.
     */
    @Test
    void bankReferenceDataRemainsAnonymous() throws Exception {
        mockMvc.perform(get("/api/banks"))
                .andExpect(status().isOk());
    }

    @Test
    void categoryReferenceDataRemainsAnonymous() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtWithRole(String role) {
        return jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }
}
