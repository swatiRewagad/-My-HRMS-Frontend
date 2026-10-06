package com.hrms.cms.config;

import com.hrms.cms.security.KeycloakJwtAuthConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/assistance/status} is already covered by SecurityConfig — proved, not assumed.
 *
 * <h2>Why this exists as its own file</h2>
 * Adding {@code /status} raised the question of whether {@link SecurityConfig} needed a new matcher
 * entry. The answer is no: the existing {@code "/api/v1/assistance/**"} entry in the STAFF_ROLES block
 * is an Ant pattern whose {@code **} spans one or more path segments, so {@code /status} is inside it
 * exactly as {@code /rail} and {@code /rail/memory} are. SecurityConfig was therefore NOT edited —
 * it is a file several concurrent sessions touch, and an unnecessary additive matcher there is a merge
 * conflict bought for nothing.
 *
 * <p>But "the wildcard surely covers it" is the kind of assumption that produces an unauthenticated
 * endpoint, so it is asserted against the real chain rather than reasoned about. A separate file
 * rather than three methods appended to {@code SecurityConfigEnforcementTest}, for the reason that
 * file's own header gives about concurrent sessions and shared files.
 *
 * <h2>Why a slice with a stub and not the real controller</h2>
 * Same reason {@code SecurityConfigEnforcementTest} uses stubs: the subject is the request matcher, so
 * booting {@code AssistanceRailController} would drag in the service, two repositories and the
 * identity resolver without testing anything more about the rule. The companion
 * {@code AssistanceRailKillSwitchTest} covers the handler's behaviour, which needs no chain.
 *
 * <h2>{@link #refusesCitizen} is the one that proves the coverage</h2>
 * Self-verifying without needing SecurityConfig mutated — which is why no mutation was performed on a
 * file other sessions hold open. If {@code /status} fell OUTSIDE the
 * {@code /api/v1/assistance/**} entry it would land on {@code anyRequest().authenticated()}, and a
 * citizen with a tracking session satisfies that, so the request would return 200. It returns 403, so
 * the STAFF_ROLES matcher is what answered. The staff-200 and anonymous-401 tests cannot distinguish
 * the two placements: both hold under the fallback too.
 */
@WebMvcTest(controllers = AssistanceStatusAuthorizationTest.StatusStub.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {PiiDecryptionFilter.class, RateLimitFilter.class,
                           AntiAutomationFilter.class, RevocationCheckFilter.class, CorsConfig.class}))
@Import({SecurityConfig.class, KeycloakJwtAuthConverter.class,
         AssistanceStatusAuthorizationTest.StatusStub.class})
@DisplayName("SecurityConfig: /api/v1/assistance/status")
class AssistanceStatusAuthorizationTest {

    @RestController
    static class StatusStub {
        @GetMapping("/api/v1/assistance/status")
        String status() {
            return "status";
        }
    }

    /**
     * {@link SecurityConfig}'s {@code oauth2ResourceServer} needs one, and the slice has no Keycloak
     * to build it from. The tests authenticate with {@code jwt()} post-processors, which bypass
     * decoding entirely, so this only has to exist. Same reason {@code SecurityConfigEnforcementTest}
     * mocks it; without it the context fails to load and all four tests error rather than fail.
     */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("admits a staff role")
    void admitsStaff() throws Exception {
        mockMvc.perform(get("/api/v1/assistance/status").with(jwtWithRole("RBIO_OFFICER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refuses an anonymous caller")
    void refusesAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/assistance/status"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The case the {@code anyRequest().authenticated()} fallback would admit, and the one that shows
     * the wildcard is doing the work. Not a disclosure of consequence on its own — it is a boolean —
     * but a citizen or an RE user cannot see the rail under any switch setting, so {@code true} would
     * be a false answer for them, and an endpoint sitting outside the namespace's matcher is a
     * precedent the next endpoint inherits.
     */
    @Test
    @DisplayName("refuses an authenticated citizen")
    void refusesCitizen() throws Exception {
        mockMvc.perform(get("/api/v1/assistance/status").with(jwtWithRole("CITIZEN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("refuses a regulated-entity user")
    void refusesRegulatedEntityUser() throws Exception {
        mockMvc.perform(get("/api/v1/assistance/status").with(jwtWithRole("RE_USER")))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtWithRole(String role) {
        return jwt().authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }
}
