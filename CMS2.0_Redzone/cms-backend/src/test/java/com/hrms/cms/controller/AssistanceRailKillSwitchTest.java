package com.hrms.cms.controller;

import com.hrms.cms.dto.AssistanceRailResponse;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.AssistanceRailService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The assistance rail's §6.2 kill switch: {@code cms.assistance.enabled}.
 *
 * <h2>The switch is proved by what does NOT happen</h2>
 * "Off" is not a response shape — the off-rail and a rail with nothing to say are the SAME 200 by the
 * DTO's own rule that {@code glow} is true iff {@code signals} is non-empty. So an assertion on the
 * body alone cannot tell a disabled feature from an enabled one on a quiet complaint, and a switch
 * wired to the response but not to the work would pass it. The load-bearing assertions here are
 * therefore {@code verifyNoInteractions} on the service and the identity resolver: the brief's
 * requirement is that the rail endpoints must not do work, not merely that they answer blandly.
 *
 * <h2>Unit, not {@code @WebMvcTest}</h2>
 * Deliberate, for the reason the controller's own javadoc gives about its authorization tests: a slice
 * test of this class does not import the real filter chain, so it would answer 400 where production
 * answers 403 and prove nothing. Authorization for {@code /api/v1/assistance/**} — including the new
 * {@code /status} path, which the existing wildcard matcher already covers — is asserted in
 * {@code SecurityConfigEnforcementTest} against the real {@code SecurityConfig}. What is left for this
 * file is the switch's behaviour, which needs no chain at all.
 *
 * <p>{@code @Value} is injected by reflection because the field is injected by Spring in production:
 * the class is {@code @RequiredArgsConstructor} and Lombok does not copy {@code @Value} onto generated
 * constructor parameters, so there is no constructor argument to pass here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssistanceRailController: the §6.2 kill switch")
class AssistanceRailKillSwitchTest {

    private static final String COMPLAINT = "CMP-20260928-340970";

    @Mock private AssistanceRailService railService;
    @Mock private RequestIdentityResolver identityResolver;

    private AssistanceRailController controller(boolean enabled) {
        AssistanceRailController c = new AssistanceRailController(railService, identityResolver);
        ReflectionTestUtils.setField(c, "assistanceEnabled", enabled);
        return c;
    }

    private HttpServletRequest request() {
        return new MockHttpServletRequest();
    }

    @Nested
    @DisplayName("GET /status")
    class Status {

        /** The frozen contract the frontend is already written against: {@code {"available": bool}}. */
        @Test
        @DisplayName("reports available: true when the switch is on")
        void reportsAvailableWhenOn() {
            ResponseEntity<Map<String, Object>> response = controller(true).status();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("available", true);
        }

        @Test
        @DisplayName("reports available: false when the switch is off")
        void reportsUnavailableWhenOff() {
            ResponseEntity<Map<String, Object>> response = controller(false).status();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("available", false);
        }

        /**
         * The key name is the whole contract. {@code AssistanceRailService.available()} on the client
         * reads {@code res?.available === true} and nothing else, so a rename to {@code isAvailable}
         * or {@code status} would read as PERMANENTLY DISABLED on every screen — the frontend's
         * safe-direction degradation turning a serialisation slip into a silently absent feature.
         */
        @Test
        @DisplayName("answers exactly one key, named available, and no provider field")
        void bodyShapeIsFrozen() {
            assertThat(controller(true).status().getBody())
                    .as("the frontend reads res.available and nothing else")
                    .containsOnlyKeys("available");
        }

        /**
         * A status endpoint must not be a second thing that can break. It reports the SWITCH, so it
         * neither probes the database nor consults the service — which is also why it is safe to call
         * on every screen load.
         */
        @Test
        @DisplayName("touches neither the service nor the identity resolver")
        void statusDoesNoWork() {
            controller(true).status();
            controller(false).status();

            verifyNoInteractions(railService, identityResolver);
        }
    }

    @Nested
    @DisplayName("GET /rail with the switch off")
    class RailWhenOff {

        /**
         * The requirement. A switch that only hid the bulb client-side would still run both tiers'
         * queries for any client that asked — a bookmarked URL, a stale bundle — which is the cost the
         * kill switch exists to remove.
         */
        @Test
        @DisplayName("does no work: no service call, no identity resolution")
        void doesNotComputeAnything() {
            controller(false).rail(COMPLAINT, request());

            verify(railService, never()).rail(anyString(), anyString(), any());
            verifyNoInteractions(railService, identityResolver);
        }

        /**
         * An empty rail, not a refusal. The DTO makes "disabled" and "nothing to say" the same state
         * by construction — {@code glow} is true iff {@code signals} is non-empty — so there is no
         * third state to express, and the endpoint keeps its single documented success shape for a
         * client that never read {@code /status}.
         */
        @Test
        @DisplayName("answers 200 with a non-glowing empty rail, never a 4xx")
        void answersAnEmptyNonGlowingRail() {
            ResponseEntity<AssistanceRailResponse> response = controller(false).rail(COMPLAINT, request());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            AssistanceRailResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.glow()).isFalse();
            assertThat(body.signals()).isEmpty();
            assertThat(body.count()).isZero();
            assertThat(body.complaintNumber()).isEqualTo(COMPLAINT);
        }

        /**
         * {@code glow == !signals.isEmpty()} must hold in the disabled case too. A rail that glowed
         * with nothing behind it trains officers to ignore the bulb, which is the one failure mode that
         * makes the feature worthless rather than merely imperfect.
         */
        @Test
        @DisplayName("keeps glow consistent with signals")
        void glowAgreesWithSignals() {
            AssistanceRailResponse body = controller(false).rail(COMPLAINT, request()).getBody();

            assertThat(body).isNotNull();
            assertThat(body.glow()).isEqualTo(!body.signals().isEmpty());
        }
    }

    @Nested
    @DisplayName("PUT /rail/memory with the switch off")
    class MemoryWhenOff {

        @Test
        @DisplayName("writes nothing")
        void writesNothing() {
            controller(false).rememberVisit(
                    Map.of("complaintNumber", COMPLAINT, "section", "assessment"), request());

            verifyNoInteractions(railService, identityResolver);
        }

        /**
         * {@code success: false} because the row was not written, which is simply true. Reporting
         * {@code true} would be the documented {@code StaffDraftController} defect in a new place — a
         * confirmation shown for a save that never happened.
         */
        @Test
        @DisplayName("answers 200 with success: false, which is the truth")
        void reportsTheWriteDidNotHappen() {
            ResponseEntity<Map<String, Object>> response = controller(false).rememberVisit(
                    Map.of("complaintNumber", COMPLAINT), request());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("success", false);
        }
    }

    @Nested
    @DisplayName("with the switch on, nothing is gated")
    class WhenOn {

        /**
         * The counterweight. Every assertion above is satisfied by a controller that is permanently
         * off, so without this the switch could ship stuck closed and the suite would stay green.
         */
        @Test
        @DisplayName("GET /rail computes and returns the service's signals")
        void railStillComputes() {
            when(identityResolver.resolve(any())).thenReturn(RequestIdentity.builder()
                    .userId("rbio.officer.001")
                    .primaryRole("RBIO_OFFICER")
                    .roles(Set.of("RBIO_OFFICER"))
                    .side("RBI")
                    .build());
            when(railService.rail(eq(COMPLAINT), eq("rbio.officer.001"), any())).thenReturn(
                    AssistanceRailResponse.of(COMPLAINT, java.util.List.of(
                            AssistanceRailResponse.Signal.memory(
                                    AssistanceRailService.KIND_LAST_SECTION,
                                    "You were last in Assessment", null, null,
                                    Map.of(AssistanceRailService.PARAM_SECTION, "Assessment")))));

            ResponseEntity<AssistanceRailResponse> response = controller(true).rail(COMPLAINT, request());

            AssistanceRailResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.glow()).isTrue();
            assertThat(body.signals()).hasSize(1);
            // The ROLES are asserted, not just the user id. The next-action prior is keyed on them, and
            // a controller that resolved the identity but dropped its roles would silence that one
            // signal while leaving every other assertion here green.
            verify(railService).rail(COMPLAINT, "rbio.officer.001", Set.of("RBIO_OFFICER"));
        }

        @Test
        @DisplayName("PUT /rail/memory reaches the service and reports its verdict")
        void memoryStillWrites() {
            when(identityResolver.resolve(any())).thenReturn(RequestIdentity.builder()
                    .userId("rbio.officer.001")
                    .primaryRole("RBIO_OFFICER")
                    .roles(Set.of("RBIO_OFFICER"))
                    .side("RBI")
                    .build());
            when(railService.rememberVisit(COMPLAINT, "rbio.officer.001", "assessment", "half a line"))
                    .thenReturn(true);

            ResponseEntity<Map<String, Object>> response = controller(true).rememberVisit(
                    Map.of("complaintNumber", COMPLAINT,
                            "section", "assessment",
                            "draftText", "half a line"),
                    request());

            assertThat(response.getBody()).containsEntry("success", true);
            verify(railService).rememberVisit(COMPLAINT, "rbio.officer.001", "assessment", "half a line");
        }
    }
}
