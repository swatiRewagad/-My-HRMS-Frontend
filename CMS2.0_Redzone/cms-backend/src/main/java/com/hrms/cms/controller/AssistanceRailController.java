package com.hrms.cms.controller;

import com.hrms.cms.dto.AssistanceRailResponse;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.AssistanceRailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The assistance rail (Brief 21): a read for what is worth saying, and a write for continuity state.
 *
 * <h2>Authorization is NOT here</h2>
 * There is no {@code @PreAuthorize} on this class, deliberately. {@code @EnableMethodSecurity} is
 * absent from this application, so every such annotation in the codebase is INERT — 31 of them are
 * documented as dead. Adding one would read as a control while enforcing nothing. The real control is
 * the matcher for {@code /api/v1/assistance/**} in {@link com.hrms.cms.config.SecurityConfig}, gated
 * to {@code STAFF_ROLES}, and it is asserted in {@code SecurityConfigEnforcementTest} rather than in a
 * slice test of this controller — a {@code @WebMvcTest} of this class does not import the real chain,
 * so it would report 400 where production returns 403 and prove nothing.
 *
 * <p>That matcher is load-bearing and not a formality. The {@code anyRequest().authenticated()}
 * fallback would admit an authenticated CITIZEN, and Tier 1 reports aggregate facts about OTHER
 * complaints — how many an entity closed under a clause, how long a category takes. Those are staff
 * analytics, not the complainant's own data.
 *
 * <h2>The caller is resolved, never declared</h2>
 * Both endpoints derive the officer from {@link RequestIdentityResolver} (token-authoritative;
 * {@code X-User-*} honoured only under dev-local via {@code cms.security.allow-dev-identity-headers}).
 * Neither accepts a user id, and the write body carries only the complaint, section and draft text.
 * Tier 0 is one officer's unsaved work, so an owner the caller could name would be no owner at all.
 *
 * <h2>The kill switch</h2>
 * {@code cms.assistance.enabled} is this feature's independent switch per Brief 21 §6.2, exposed by
 * {@link #status()} and read by {@link #rail} and {@link #rememberVisit} before they do any work. The
 * naming follows the {@code cms.similar-cases.enabled} precedent rather than inventing a convention,
 * and so does the Java-side fallback: {@code ElasticsearchSimilarCasesProvider} defaults its flag to
 * {@code false}, and so does this, so a stale ConfigMap missing the key lands on "hide the affordance"
 * rather than on a feature nobody chose to enable.
 *
 * <p>The DEFAULT differs from similar-cases on purpose: that one ships {@code true} in
 * {@code application.yml} and this one ships {@code false}. Similar cases is user-initiated, so §2.5
 * makes a failure something the panel must SAY; the rail is ambient, so §5.1 makes a failure something
 * it must stay SILENT about. A feature that fails silently cannot be observed failing, so it is turned
 * on per environment by someone who has looked, not by a default.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistance")
@RequiredArgsConstructor
public class AssistanceRailController {

    private final AssistanceRailService railService;
    private final RequestIdentityResolver identityResolver;

    /**
     * The §6.2 kill switch. Defaults to {@code false} — see the class javadoc for why this default is
     * the safe one here while {@code cms.similar-cases.enabled} ships {@code true}.
     *
     * <p>Field injection rather than a constructor parameter because the class is
     * {@code @RequiredArgsConstructor}: Lombok does not copy {@code @Value} onto generated constructor
     * parameters (there is no {@code lombok.config} enabling {@code copyableAnnotations} in this repo),
     * so a {@code final} field would be injected as null. This is the same shape
     * {@code AaParentComplaintController} uses for the same reason.
     */
    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * Whether the assistance feature is switched on, so the frontend can hide the affordance (§6.2).
     *
     * <p>Shaped {@code {"available": <bool>}} to match {@code SimilarCasesController#getStatus}, minus
     * its {@code provider} field: similar cases genuinely has providers to name, whereas the rail has
     * no backend to be swapped and reporting one would be inventing a fact. {@code available} is the
     * key the frontend's {@code AssistanceRailService.available()} reads.
     *
     * <p>Reports the SWITCH and nothing more — no probe, no repository touch, no "can I reach MySQL".
     * A status endpoint that tested liveness would be a second failure path in front of a feature whose
     * entire contract is to fail quietly, and the frontend already degrades a transport error to
     * {@code false} on its own.
     *
     * <p>Staff-only like the rest of this namespace, via the {@code /api/v1/assistance/**} matcher.
     * That is not over-locking a boolean: it is the only honest place to put it, since a citizen or an
     * RE user cannot see the rail under any switch setting, so the answer would be a lie for them.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", assistanceEnabled);
        return ResponseEntity.ok(response);
    }

    /**
     * What the rail has to say about one complaint, to this officer.
     *
     * <p>Always 200. An unresolvable identity, an unknown complaint number and an internal failure all
     * yield {@code glow: false} with an empty {@code signals} list rather than a 4xx or 5xx, because
     * the rail sits beside the real screen and must never be the reason an officer cannot work a
     * complaint. A 400 would also be indistinguishable from a malformed request, since
     * {@code GlobalExceptionHandler} maps bare {@code RuntimeException} to 400.
     *
     * <p>Degrading to a quiet rail is safe in a way degrading to a NOISY one would not be: a missing
     * signal costs a convenience, whereas a fabricated one would be read as fact.
     *
     * <p>With the switch off this returns {@link AssistanceRailResponse#empty} — a 200 carrying
     * {@code glow: false} and no signals — WITHOUT calling the service, so neither tier issues a
     * query. An empty rail rather than a 403 or a 404, for two reasons. First, the DTO's own rule is
     * that {@code glow} is true iff {@code signals} is non-empty, so a disabled rail and a rail with
     * nothing to say are the SAME state by construction and there is nothing to distinguish; a
     * refusal would be inventing a third state the contract does not have. Second, a client that
     * never reads {@code /status} — a bookmarked URL, an old bundle, a smoke test — must still get the
     * single documented success shape, because this endpoint is read by a screen that must stay usable.
     * The frontend hides the bulb from {@code /status}; this is the server making sure that even an
     * unaware client cannot make a disabled feature look broken.
     */
    @GetMapping("/rail")
    public ResponseEntity<AssistanceRailResponse> rail(
            @RequestParam("complaintId") String complaintId,
            HttpServletRequest request) {

        if (!assistanceEnabled) {
            // No service call at all: "the rail endpoints must not do work" is the point of the switch,
            // so this returns before identity resolution as well as before either tier's queries.
            return ResponseEntity.ok(AssistanceRailResponse.empty(complaintId));
        }

        try {
            // Null when the identity could not be established. Passed through as-is: the service
            // returns no Tier 0 for an unresolved owner rather than guessing, and the three
            // role-independent priors still render, so an anonymous-but-authorized caller is not
            // served an empty rail.
            RequestIdentity identity = identityResolver.resolve(request);
            String userId = identity == null ? null : identity.getUserId();
            // ALL the caller's roles, not getPrimaryRole(). That field is roles.iterator().next() over
            // a HashSet, so for a multi-role officer it names an arbitrary one and can name a different
            // one across JVMs — the rail would report a prior for a role the officer merely holds
            // rather than the one they are working as, and would appear to change its mind for no
            // reason. Handing over the whole set lets the service apply a rule it can state; see
            // AssistanceRailService#nextAction.
            Set<String> roles = identity == null ? null : identity.getRoles();
            return ResponseEntity.ok(railService.rail(complaintId, userId, roles));
        } catch (Exception e) {
            // The service already guards each tier; this is the outer net that keeps the contract's
            // single success shape true even if resolution itself fails.
            log.warn("Assistance rail read failed for {}: {}", complaintId, e.toString());
            return ResponseEntity.ok(AssistanceRailResponse.empty(complaintId));
        }
    }

    /**
     * Records the officer's continuity state as they leave a screen (Tier 0 write).
     *
     * <p>Reports {@code success: false} rather than an error status when the write did not happen —
     * typically an unresolved identity. The frontend calls this during navigation, where there is no
     * screen left to show an error on, and the caller is told the truth without being interrupted.
     * The inverse bug is documented on {@code StaffDraftController}: a component that set
     * {@code draftSaved(true)} in its error handler showed a confirmation for a save that failed. Here
     * the flag is the server's answer, so the client cannot invent one.
     *
     * <p>With the switch off this writes nothing and answers {@code success: false}, which is simply
     * true — the row was not written. Reporting {@code true} would be the exact defect named above in a
     * new place. Note that this is NOT a draft-loss risk: Tier 0 memory is a continuity HINT, and the
     * officer's actual draft is persisted by {@code StaffDraftController}, which is a separate feature
     * with its own endpoint and is not gated by this switch.
     */
    @PutMapping("/rail/memory")
    public ResponseEntity<Map<String, Object>> rememberVisit(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        Map<String, Object> response = new LinkedHashMap<>();

        if (!assistanceEnabled) {
            response.put("success", false);
            return ResponseEntity.ok(response);
        }

        try {
            String userId = resolveUserIdOrNull(request);
            String complaintNumber = asText(body, "complaintNumber");
            String section = asText(body, "section");
            String draftText = asText(body, "draftText");

            boolean written = railService.rememberVisit(complaintNumber, userId, section, draftText);
            response.put("success", written);
        } catch (Exception e) {
            log.warn("Assistance rail memory write failed: {}", e.toString());
            response.put("success", false);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * The caller's id, or null.
     *
     * <p>Does not throw on an unresolved identity, which is where this differs from
     * {@code StaffDraftController#resolveIdentity} — and the difference is deliberate. A draft SAVE
     * must fail closed, because a draft with no owner cannot be kept private. A rail READ fails QUIET:
     * Tier 0 is simply omitted (the service returns nothing for a null owner) while Tier 1, which is
     * not per-user, still renders. Neither path ever attributes state to a placeholder owner.
     */
    private String resolveUserIdOrNull(HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        return identity == null ? null : identity.getUserId();
    }

    private String asText(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }
}
