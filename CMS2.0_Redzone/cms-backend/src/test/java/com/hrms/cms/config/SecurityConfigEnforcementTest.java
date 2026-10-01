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

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

        // ── Similar cases: staff only (Brief 21) ──
        // This endpoint returns OTHER complaints' numbers and subjects as precedent, so the matcher
        // keeping it inside STAFF_ROLES is a disclosure control, not a convenience. Both routes are
        // stubbed: /search reads complaint text, and /status reveals whether a search backend exists.

        @PostMapping("/api/v1/similar-cases/search")
        String similarCasesSearch() {
            return "similar-search";
        }

        @GetMapping("/api/v1/similar-cases/status")
        String similarCasesStatus() {
            return "similar-status";
        }

        // ── Assistance rail: staff only (Brief 21) ──
        // Tier 1 of the rail reports aggregate facts about OTHER complaints — how many an entity closed
        // under a given clause, how long a category takes to close — so the matcher is a disclosure
        // control, not a convenience. Tier 0 is one officer's own last section and unsaved text, which
        // is additionally scoped to the resolved principal inside AssistanceRailService. Both verbs are
        // stubbed because the read and the Tier 0 write are separate handlers.

        @GetMapping("/api/v1/assistance/rail")
        String assistanceRail() {
            return "rail";
        }

        @PutMapping("/api/v1/assistance/rail/memory")
        String assistanceRailMemory() {
            return "rail-memory";
        }

        // ── Master data: anonymous READ, admin WRITE (UST456) ──
        // Reads and writes are separate handlers on the same paths because the whole point of the fix
        // is that the verb decides. A single handler could not express "GET open, POST closed".

        @GetMapping("/api/v1/masters/categories")
        String masterCategoriesRead() {
            return "master-read";
        }

        @PostMapping("/api/v1/masters/categories")
        String masterCategoriesCreate() {
            return "master-create";
        }

        @PutMapping("/api/v1/masters/categories/1")
        String masterCategoriesUpdate() {
            return "master-update";
        }

        @DeleteMapping("/api/v1/masters/categories/1")
        String masterCategoriesDelete() {
            return "master-delete";
        }

        @GetMapping("/api/form-config/complaint")
        String formConfigRead() {
            return "form-read";
        }

        @PutMapping("/api/form-config/complaint")
        String formConfigUpdate() {
            return "form-update";
        }

        @GetMapping("/api/v1/tat/holidays/2026")
        String holidaysRead() {
            return "holidays-read";
        }

        @PostMapping("/api/v1/tat/holidays")
        String holidayCreate() {
            return "holiday-create";
        }

        @DeleteMapping("/api/v1/tat/holidays/1")
        String holidayDelete() {
            return "holiday-delete";
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

    // ───────────── Master data: read stays open, writes are closed (UST456) ─────────────
    //
    // Before this, /api/v1/masters/** was permitAll for EVERY verb, so an anonymous caller could
    // rewrite CATEGORY_MASTER and DEPARTMENT_ROUTING_MASTER — the tables that decide which office a
    // complaint reaches and whether it is maintainable. The @PreAuthorize on MasterDataController did
    // not stop it: @EnableMethodSecurity is absent from this application, so all 31 @PreAuthorize
    // annotations in the codebase are inert. Only the filter chain runs, which is why these assertions
    // live here and not in a controller test.

    @Test
    void masterDataReadStaysAnonymousForTheCitizenFilingForm() throws Exception {
        // The public wizard needs the category list before anyone signs in. If this ever turns 401,
        // the fix has overreached and citizens cannot file.
        mockMvc.perform(get("/api/v1/masters/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void masterDataCreateRejectsAnonymousCaller() throws Exception {
        mockMvc.perform(post("/api/v1/masters/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void masterDataUpdateRejectsAnonymousCaller() throws Exception {
        mockMvc.perform(put("/api/v1/masters/categories/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void masterDataDeleteRejectsAnonymousCaller() throws Exception {
        mockMvc.perform(delete("/api/v1/masters/categories/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void masterDataWriteRejectsAnOrdinaryStaffRole() throws Exception {
        // Being signed in is not enough. A dealing official must not be able to re-route every future
        // complaint by editing DEPARTMENT_ROUTING_MASTER.
        mockMvc.perform(post("/api/v1/masters/categories").with(jwtWithRole("RBIO_DEALING_OFFICIAL")))
                .andExpect(status().isForbidden());
    }

    @Test
    void masterDataWriteAdmitsTheOmbudsmanAdmin() throws Exception {
        // UST456's actor. RBIO_ADMIN is the Ombudsman Admin; the realm has no separate token for it.
        mockMvc.perform(post("/api/v1/masters/categories").with(jwtWithRole("RBIO_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void masterDataWriteStillAdmitsCrpcAdmin() throws Exception {
        // The dead @PreAuthorize named CRPC_ADMIN. Narrowing an existing permission while closing a
        // hole would silently break CRPC master upkeep, so it is preserved deliberately.
        mockMvc.perform(post("/api/v1/masters/categories").with(jwtWithRole("CRPC_ADMIN")))
                .andExpect(status().isOk());
    }

    // ───────────── FORM_CONFIG: the citizen form's own schema (UST456) ─────────────

    @Test
    void formConfigReadStaysAnonymous() throws Exception {
        mockMvc.perform(get("/api/form-config/complaint"))
                .andExpect(status().isOk());
    }

    @Test
    void formConfigUpdateRejectsAnonymousCaller() throws Exception {
        // PUT /api/form-config/{formKey} had no authorization of any kind and sat under permitAll, so
        // an anonymous caller could rewrite the fields and validation a complainant files against.
        mockMvc.perform(put("/api/form-config/complaint"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void formConfigUpdateRejectsAnOrdinaryStaffRole() throws Exception {
        mockMvc.perform(put("/api/form-config/complaint").with(jwtWithRole("RBIO_REVIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void formConfigUpdateAdmitsTheOmbudsmanAdmin() throws Exception {
        mockMvc.perform(put("/api/form-config/complaint").with(jwtWithRole("RBIO_ADMIN")))
                .andExpect(status().isOk());
    }

    // ───────────── HOLIDAYS: master data that moves every statutory deadline ─────────────

    @Test
    void holidayReadRemainsOpenToStaff() throws Exception {
        mockMvc.perform(get("/api/v1/tat/holidays/2026").with(jwtWithRole("RBIO_OFFICER")))
                .andExpect(status().isOk());
    }

    @Test
    void holidayCreateRejectsAnOrdinaryStaffRole() throws Exception {
        // BusinessHoursService reads HOLIDAYS for every statutory deadline in the system — the RE
        // response window, TAT, SLA. One spurious row silently moves every deadline on every
        // complaint, so this is not an ordinary staff action even though /api/v1/tat/** is a staff path.
        mockMvc.perform(post("/api/v1/tat/holidays").with(jwtWithRole("RBIO_OFFICER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void holidayDeleteRejectsAnOrdinaryStaffRole() throws Exception {
        // Deleting a genuine gazetted holiday is the more dangerous direction: it makes deadlines fall
        // EARLIER than the Scheme allows, shortening a window the entity or citizen was promised.
        mockMvc.perform(delete("/api/v1/tat/holidays/1").with(jwtWithRole("CEPC_DO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void holidayWriteAdmitsTheMasterAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/tat/holidays").with(jwtWithRole("RBIO_ADMIN")))
                .andExpect(status().isOk());
    }

    // ───────────── Keycloak user directory reaches the Ombudsman Admin (UST443-455) ─────────────

    @Test
    void keycloakUserApiAdmitsTheOmbudsmanAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/keycloak/users/all").with(jwtWithRole("RBIO_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void keycloakUserApiStillRejectsAnOrdinaryRbioRole() throws Exception {
        // Widening to RBIO_ADMIN must not widen to every RBIO role — the user directory is not a
        // dealing official's screen.
        mockMvc.perform(get("/api/v1/keycloak/users/all").with(jwtWithRole("RBIO_DEALING_OFFICIAL")))
                .andExpect(status().isForbidden());
    }

    // ───────────── Similar cases is staff-only precedent, not public data (Brief 21) ─────────────
    //
    // Written as refusals on purpose. A guard that fails OPEN still passes a happy-path test, because
    // a guard admitting everyone admits the right role too. Only the denials can detect it.

    @Test
    void similarCasesAdmitsStaff() throws Exception {
        mockMvc.perform(post("/api/v1/similar-cases/search").with(jwtWithRole("CEPC_REVIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    void similarCasesRefusesAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/similar-cases/search"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The case the {@code anyRequest().authenticated()} fallback would admit.
     *
     * <p>A citizen holding a tracking session is authenticated. If the STAFF_ROLES matcher for
     * {@code /api/v1/similar-cases/**} were ever removed or reordered below the fallback, this request
     * would succeed and one citizen's complaint subjects would be readable by another.
     */
    @Test
    void similarCasesRefusesAnAuthenticatedCitizen() throws Exception {
        mockMvc.perform(post("/api/v1/similar-cases/search").with(jwtWithRole("CITIZEN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void similarCasesRefusesARegulatedEntityUser() throws Exception {
        mockMvc.perform(post("/api/v1/similar-cases/search").with(jwtWithRole("RE_USER")))
                .andExpect(status().isForbidden());
    }

    /** Availability of a search backend is not public metadata either. */
    @Test
    void similarCasesStatusIsAlsoStaffOnly() throws Exception {
        mockMvc.perform(get("/api/v1/similar-cases/status").with(jwtWithRole("CITIZEN")))
                .andExpect(status().isForbidden());
    }

    // ───────────── Assistance rail is staff-only aggregate data (Brief 21) ─────────────
    //
    // Written as refusals first, for the reason the similar-cases block above states: a guard that
    // fails OPEN still passes a happy-path test, because a guard admitting everyone admits the right
    // role too. Only the denials can detect it.
    //
    // These assertions live HERE and not in a controller slice deliberately. A @WebMvcTest of
    // AssistanceRailController does not import the real SecurityConfig, so it returns 400 where
    // production returns 403 — it would pass while proving nothing about authorization.
    //
    // MUTATION-VERIFIED: deleting "/api/v1/assistance/**" from the STAFF_ROLES matcher in
    // SecurityConfig makes the four refusal tests below fail with 200 instead of 403 — the request
    // falls through to anyRequest().authenticated(), which a CITIZEN and an RE_USER both satisfy. The
    // 401 and staff-200 tests keep passing under that mutation, which is precisely why the refusals
    // are the ones that matter.

    @Test
    void assistanceRailAdmitsStaff() throws Exception {
        mockMvc.perform(get("/api/v1/assistance/rail").param("complaintId", "CMP-1")
                        .with(jwtWithRole("RBIO_OFFICER")))
                .andExpect(status().isOk());
    }

    @Test
    void assistanceRailRefusesAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/assistance/rail").param("complaintId", "CMP-1"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The case the {@code anyRequest().authenticated()} fallback would admit.
     *
     * <p>A citizen holding a tracking session is authenticated. If the STAFF_ROLES matcher for
     * {@code /api/v1/assistance/**} were ever removed or reordered below the fallback, this request
     * would succeed and one citizen would read staff analytics over the whole register — how many
     * complaints an entity closed under a clause, how long a category takes.
     */
    @Test
    void assistanceRailRefusesAnAuthenticatedCitizen() throws Exception {
        mockMvc.perform(get("/api/v1/assistance/rail").param("complaintId", "CMP-1")
                        .with(jwtWithRole("CITIZEN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void assistanceRailRefusesARegulatedEntityUser() throws Exception {
        mockMvc.perform(get("/api/v1/assistance/rail").param("complaintId", "CMP-1")
                        .with(jwtWithRole("RE_USER")))
                .andExpect(status().isForbidden());
    }

    /**
     * The Tier 0 WRITE path needs its own assertions, not just the read's.
     *
     * <p>It is a different verb on a different sub-path, and a matcher written as a GET-only rule would
     * leave it to the authenticated fallback — where a citizen could write rows into an officer's
     * continuity table.
     */
    @Test
    void assistanceRailMemoryWriteAdmitsStaff() throws Exception {
        mockMvc.perform(put("/api/v1/assistance/rail/memory").with(jwtWithRole("CEPC_REVIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    void assistanceRailMemoryWriteRefusesAnonymous() throws Exception {
        mockMvc.perform(put("/api/v1/assistance/rail/memory"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assistanceRailMemoryWriteRefusesAnAuthenticatedCitizen() throws Exception {
        mockMvc.perform(put("/api/v1/assistance/rail/memory").with(jwtWithRole("CITIZEN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void assistanceRailMemoryWriteRefusesARegulatedEntityUser() throws Exception {
        mockMvc.perform(put("/api/v1/assistance/rail/memory").with(jwtWithRole("RE_USER")))
                .andExpect(status().isForbidden());
    }

    /**
     * Dev identity headers must carry no authority on the rail either.
     *
     * <p>Specific to Tier 0: {@code RequestIdentityResolver} honours {@code X-User-Id} when
     * {@code cms.security.allow-dev-identity-headers} is true, which is exactly the mechanism that
     * would let a caller name another officer and read their unsaved text. Under the enforcing chain
     * the request never reaches the resolver at all.
     */
    @Test
    void assistanceRailIgnoresDevIdentityHeadersFromAnAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/v1/assistance/rail").param("complaintId", "CMP-1")
                        .header("X-User-Id", "another.officer")
                        .header("X-User-Roles", "RBIO_OFFICER"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtWithRole(String role) {
        return jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }
}
