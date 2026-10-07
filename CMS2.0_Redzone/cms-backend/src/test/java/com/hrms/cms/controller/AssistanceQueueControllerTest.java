package com.hrms.cms.controller;

import com.hrms.cms.dto.AssistanceQueueResponse;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.AssistanceQueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AssistanceQueueController}: the kill switch, the resolved caller, and the quiet failure.
 *
 * <h2>The switch is proved by what does NOT happen</h2>
 * "Off" is not a response shape — a disabled feature and a queue with nothing to say are the SAME 200,
 * by the DTO's own rule that {@code glow} is true iff {@code signals} is non-empty. So an assertion on
 * the body alone cannot tell a disabled feature from an enabled one on a quiet queue, and a switch
 * wired to the response but not to the work would pass it. The load-bearing assertions are therefore
 * {@code verifyNoInteractions} on BOTH the service and the identity resolver: §6.2's requirement is
 * that the endpoints must not do work, not merely that they answer blandly.
 *
 * <h2>Unit, not {@code @WebMvcTest}</h2>
 * Deliberate, for the reason the rail controller's javadoc gives: a slice test does not import the real
 * filter chain, so it would answer 400 where production answers 403 and prove nothing. Authorization
 * for {@code /api/v1/assistance/**} — which already covers {@code /queue} without any
 * {@code SecurityConfig} edit — is asserted in {@code SecurityConfigEnforcementTest} against the real
 * chain, and the 60/min bucket in {@code RateLimitFilterAssistanceBucketTest}.
 *
 * <p>{@code @Value} is set by reflection because the field is injected by Spring in production: the
 * class is {@code @RequiredArgsConstructor} and Lombok does not copy {@code @Value} onto generated
 * constructor parameters, so there is no constructor argument to pass.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssistanceQueueController: switch, identity and silence")
class AssistanceQueueControllerTest {

    @Mock private AssistanceQueueService queueService;
    @Mock private RequestIdentityResolver identityResolver;

    private AssistanceQueueController controller(boolean enabled) {
        AssistanceQueueController c = new AssistanceQueueController(queueService, identityResolver);
        ReflectionTestUtils.setField(c, "assistanceEnabled", enabled);
        return c;
    }

    private static AssistanceQueueResponse oneSignal() {
        return AssistanceQueueResponse.of(List.of(new AssistanceQueueResponse.Signal(
                AssistanceQueueService.KIND_DEADLINE_TRIAGE,
                "3 of your 14 cases breach within 48h",
                null, 3L, "/complaints?deadlineWithinHours=48",
                Map.of("count", "3", "total", "14", "hours", "48", "overdue", "0"))));
    }

    @Nested
    @DisplayName("the kill switch")
    class KillSwitch {

        /**
         * The default is {@code false}, matching the rail's. This is an ambient affordance that fails
         * SILENTLY, and a feature that fails silently cannot be observed failing, so it is turned on
         * per environment by someone who has looked rather than by a default.
         */
        @Test
        @DisplayName("the field defaults to false when nothing injects it")
        void theDefaultIsOff() {
            AssistanceQueueController c =
                    new AssistanceQueueController(queueService, identityResolver);

            assertThat(ReflectionTestUtils.getField(c, "assistanceEnabled")).isEqualTo(false);
        }

        /** THE assertion: off means no query, and not even an identity resolution. */
        @Test
        @DisplayName("with the switch off neither the service nor the resolver is touched")
        void offDoesNoWorkAtAll() {
            ResponseEntity<AssistanceQueueResponse> response =
                    controller(false).queue(new MockHttpServletRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().signals()).isEmpty();
            assertThat(response.getBody().glow()).isFalse();
            verifyNoInteractions(queueService);
            verifyNoInteractions(identityResolver);
        }

        /**
         * A 200 with an empty payload rather than a 403 or 404. The contract has no third state: a
         * disabled feature and a queue with nothing to say are identical by construction, so a refusal
         * would invent one. A client that never reads {@code /status} — a bookmarked URL, an old
         * bundle, a smoke test — must still get the single documented success shape.
         */
        @Test
        @DisplayName("off answers 200 with the documented empty shape, not a refusal")
        void offIsNotARefusal() {
            ResponseEntity<AssistanceQueueResponse> response =
                    controller(false).queue(new MockHttpServletRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().count()).isZero();
        }

        @Test
        @DisplayName("with the switch on the service is consulted")
        void onDelegates() {
            when(queueService.triage(any(), any())).thenReturn(oneSignal());
            when(identityResolver.resolve(any())).thenReturn(identity("cepc_do_001", Set.of("CEPC_DO")));

            ResponseEntity<AssistanceQueueResponse> response =
                    controller(true).queue(new MockHttpServletRequest());

            assertThat(response.getBody().signals()).hasSize(1);
            assertThat(response.getBody().glow()).isTrue();
        }
    }

    @Nested
    @DisplayName("the caller is resolved, never declared")
    class Identity {

        /**
         * The whole role SET reaches the service, never {@code getPrimaryRole()}. That field is
         * {@code roles.iterator().next()} over a {@code HashSet}, so for a multi-role officer it names
         * an arbitrary one and can name a DIFFERENT one across JVMs — the count would describe a queue
         * the officer merely has access to, and would appear to change between page loads.
         */
        @Test
        @DisplayName("every role is forwarded, not the arbitrary primary one")
        void theWholeRoleSetIsForwarded() {
            Set<String> roles = Set.of("CEPC_DO", "CEPC_REVIEWER", "offline_access");
            when(identityResolver.resolve(any())).thenReturn(identity("cepc_do_001", roles));
            when(queueService.triage(any(), any())).thenReturn(AssistanceQueueResponse.empty());

            controller(true).queue(new MockHttpServletRequest());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
            verify(queueService).triage(eq("cepc_do_001"), captor.capture());
            assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(roles);
        }

        /**
         * An unresolved identity forwards NULL rather than a placeholder owner. The service then counts
         * nothing. The defect this avoids is live in the same codebase:
         * {@code EmailSyndicationApiController:451} returned every row in the system when its owner was
         * omitted, because the owner was an input.
         */
        @Test
        @DisplayName("an unresolved identity forwards null, never a placeholder owner")
        void anUnresolvedIdentityForwardsNull() {
            when(identityResolver.resolve(any())).thenReturn(null);
            when(queueService.triage(any(), any())).thenReturn(AssistanceQueueResponse.empty());

            ResponseEntity<AssistanceQueueResponse> response =
                    controller(true).queue(new MockHttpServletRequest());

            verify(queueService).triage(null, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().signals()).isEmpty();
        }
    }

    /**
     * §5.1 requires an ambient affordance to be off the critical path. A 400 would also be
     * indistinguishable from a malformed request, since {@code GlobalExceptionHandler} maps bare
     * {@code RuntimeException} to 400.
     */
    @Test
    @DisplayName("a resolver failure degrades to 200-and-empty rather than propagating")
    void aResolverFailureIsCaught() {
        when(identityResolver.resolve(any())).thenThrow(new RuntimeException("token parse failed"));

        ResponseEntity<AssistanceQueueResponse> response =
                controller(true).queue(new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().signals()).isEmpty();
    }

    /** The response carries no complaint number — that absence is the reason this contract exists. */
    @Test
    @DisplayName("the payload has no per-complaint field to mis-key a queue fact against")
    void thePayloadIsQueueScoped() {
        assertThat(AssistanceQueueResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("glow", "signals", "count")
                .doesNotContain("complaintNumber");
    }

    private static RequestIdentity identity(String userId, Set<String> roles) {
        return RequestIdentity.builder()
                .userId(userId)
                .roles(roles)
                .primaryRole(roles.isEmpty() ? null : roles.iterator().next())
                .build();
    }
}
