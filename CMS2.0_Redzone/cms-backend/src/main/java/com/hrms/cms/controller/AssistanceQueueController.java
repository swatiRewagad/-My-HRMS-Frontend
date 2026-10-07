package com.hrms.cms.controller;

import com.hrms.cms.dto.AssistanceQueueResponse;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.AssistanceQueueService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * The queue-scoped assistance read (Brief 21 §5.3 item 4): deadline triage over the caller's own work.
 *
 * <h2>Its own controller, and why that is the right call rather than a method on the rail's</h2>
 * {@code AssistanceRailController} is the per-complaint rail: every response it returns is keyed to a
 * {@code complaintNumber} and its write endpoint records continuity state for one complaint. This
 * endpoint takes NO complaint and returns a different contract
 * ({@link AssistanceQueueResponse}, which has no {@code complaintNumber} at all). Putting a
 * queue-scoped read on the rail's controller would mean one class serving two contracts, and the rail's
 * is pinned — a parallel frontend was built against that exact JSON. A separate class also means the
 * two features' kill-switch behaviour, error handling and tests stay independently readable.
 *
 * <h2>Authorization is NOT here, and that is deliberate</h2>
 * There is no {@code @PreAuthorize} on this class. {@code @EnableMethodSecurity} is ABSENT from this
 * application, so every such annotation in the codebase is INERT — 31 of them are documented as dead —
 * and adding one would read as a control while enforcing nothing. The real control is the matcher for
 * {@code /api/v1/assistance/**} in {@code SecurityConfig}, gated to {@code STAFF_ROLES}, which already
 * covers this path: NO SecurityConfig EDIT IS REQUIRED, which was verified by reading the matcher
 * rather than assumed from the prefix.
 *
 * <p>That matcher is load-bearing. The {@code anyRequest().authenticated()} fallback would admit an
 * authenticated CITIZEN, and while this endpoint reports only counts about the CALLER's own queue — so
 * a citizen would get an empty response anyway, their roles mapping to no department — the refusal
 * should happen at the chain and not depend on a service's scoping being right.
 *
 * <h2>Rate limiting is NOT here either</h2>
 * {@code RateLimitFilter} buckets on {@code path.startsWith("/api/v1/assistance/")} at 60 requests per
 * minute per client IP, so this endpoint inherits the assistance bucket automatically. NO
 * RateLimitFilter EDIT IS REQUIRED. §5.1's "dismissible and rate-limited" is therefore already
 * satisfied on the server side, with dismissal handled in the frontend service.
 *
 * <h2>The caller is resolved, never declared</h2>
 * The officer and their roles come from {@link RequestIdentityResolver} (token-authoritative;
 * {@code X-User-*} honoured only under dev-local via {@code cms.security.allow-dev-identity-headers}).
 * There is NO user id, role or department parameter on this endpoint, and that absence is the control:
 * {@code EmailSyndicationApiController:451} returned every row in the system when its owner parameter
 * was omitted, because the owner was an input. A queue an officer could name would not be their queue.
 *
 * <h2>The kill switch</h2>
 * {@code cms.assistance.enabled} is reused rather than given a sibling, per the instruction and per
 * §6.2. Deadline triage is part of the assistance feature, not a feature of its own, and a second flag
 * would create a state — rail off, queue on — that nobody wants and that the frontend would have to
 * query twice to discover. Defaults to {@code false} for the same reason the rail does: this is an
 * ambient affordance that fails SILENTLY, and a feature that fails silently cannot be observed
 * failing, so it is turned on per environment by someone who has looked.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistance")
@RequiredArgsConstructor
public class AssistanceQueueController {

    private final AssistanceQueueService queueService;
    private final RequestIdentityResolver identityResolver;

    /**
     * The §6.2 kill switch, shared with the rail.
     *
     * <p>Field injection rather than a constructor parameter because the class is
     * {@code @RequiredArgsConstructor}: Lombok does not copy {@code @Value} onto generated constructor
     * parameters (there is no {@code lombok.config} enabling {@code copyableAnnotations} in this repo),
     * so a {@code final} field would be injected as null. Same shape as
     * {@code AssistanceRailController} for the same reason.
     */
    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * What the caller's queue has to say about impending deadlines.
     *
     * <p>Returns 200 with an empty payload for an unresolvable identity, a caller whose roles map to no
     * department, a queue below the floors, a queue with nothing breaching, and any internal failure.
     * The rail sits beside the real screen and must never be the reason an officer cannot work a
     * complaint, and a 400 would be indistinguishable from a malformed request since
     * {@code GlobalExceptionHandler} maps bare {@code RuntimeException} to 400.
     *
     * <p>NOT "always 200", and the exception is worth naming rather than papering over: this path sits
     * behind {@code RateLimitFilter}, which answers 429 from the FILTER chain before this method is
     * entered. A client polling the queue faster than 60/min will see 429s that no code here can turn
     * into an empty payload, so the frontend must treat a non-200 as "no signal" rather than as an
     * error worth showing. {@code SecurityConfig} can likewise answer 401/403 ahead of this method.
     *
     * <p>With the switch off this returns {@link AssistanceQueueResponse#empty()} WITHOUT calling the
     * service, so neither query is issued and identity is not even resolved — "the endpoints must not do
     * work" is the point of the switch. An empty payload rather than a 403 or 404 because the contract's
     * own rule is that {@code glow} is true iff {@code signals} is non-empty, so a disabled feature and
     * a queue with nothing to say are the SAME state by construction; a refusal would invent a third
     * state the contract does not have. A client that never reads {@code /status} — a bookmarked URL, an
     * old bundle, a smoke test — must still get the single documented success shape.
     */
    @GetMapping("/queue")
    public ResponseEntity<AssistanceQueueResponse> queue(HttpServletRequest request) {

        if (!assistanceEnabled) {
            return ResponseEntity.ok(AssistanceQueueResponse.empty());
        }

        try {
            // Null when the identity could not be established. Passed through as-is: the service
            // returns an empty payload for an unresolved caller rather than guessing a scope. Unlike
            // the per-complaint rail, there is no role-independent part of this signal to fall back
            // on — a queue with no owner is not a queue.
            RequestIdentity identity = identityResolver.resolve(request);
            String userId = identity == null ? null : identity.getUserId();
            // ALL the caller's roles, never getPrimaryRole(). That field is roles.iterator().next()
            // over a HashSet, so for a multi-role officer it names an arbitrary one and can name a
            // different one across JVMs — the count would describe a queue the officer merely has
            // access to, and would appear to change between page loads. See AssistanceQueueService.
            Set<String> roles = identity == null ? null : identity.getRoles();
            return ResponseEntity.ok(queueService.triage(userId, roles));
        } catch (Exception e) {
            // The service already swallows its own failures; this is the outer net that keeps the
            // contract's single success shape true even if identity resolution itself throws.
            log.warn("Assistance queue read failed: {}", e.toString());
            return ResponseEntity.ok(AssistanceQueueResponse.empty());
        }
    }
}
