package com.hrms.cms.controller;

import com.hrms.cms.dto.AssistanceRailResponse;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.AssistanceRailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

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
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistance")
@RequiredArgsConstructor
public class AssistanceRailController {

    private final AssistanceRailService railService;
    private final RequestIdentityResolver identityResolver;

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
     */
    @GetMapping("/rail")
    public ResponseEntity<AssistanceRailResponse> rail(
            @RequestParam("complaintId") String complaintId,
            HttpServletRequest request) {

        try {
            // Null when the identity could not be established. Passed through as-is: the service
            // returns no Tier 0 for an unresolved owner rather than guessing, and Tier 1 carries no
            // per-user content, so an anonymous-but-authorized caller still gets the priors.
            String userId = resolveUserIdOrNull(request);
            return ResponseEntity.ok(railService.rail(complaintId, userId));
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
     */
    @PutMapping("/rail/memory")
    public ResponseEntity<Map<String, Object>> rememberVisit(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        Map<String, Object> response = new LinkedHashMap<>();
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
