package com.hrms.cms.controller;

import com.hrms.cms.dto.DuplicateFilingResponse;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.DuplicateFilingDetectionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The duplicate / repeat-filing read, and its kill switch.
 *
 * <h2>Mounted under {@code /api/v1/assistance}, which is where its authorization already is</h2>
 * {@code SecurityConfig} carries a matcher for {@code /api/v1/assistance/**} gated to
 * {@code STAFF_ROLES}, asserted by {@code SecurityConfigEnforcementTest}. Mounting here means this
 * endpoint inherits a control that is already TESTED, rather than needing a new matcher in a file six
 * concurrent sessions are editing — and it also inherits the namespace's rate-limit bucket.
 *
 * <p>For THIS endpoint that matcher is not a convenience, it is the primary control. The
 * {@code anyRequest().authenticated()} fallback would admit an authenticated CITIZEN, and this read
 * returns a list of one person's complaints. A citizen reaching it could enumerate another complainant's
 * filing history by complaint number. Staff-only is the floor, and the department scope filter inside the
 * service is the second layer on top of it.
 *
 * <h2>There is no {@code @PreAuthorize} here, deliberately</h2>
 * {@code @EnableMethodSecurity} is absent from this application, so every such annotation in this
 * codebase is INERT — 31 of them are documented as dead. Adding one to a PII-bearing endpoint would be
 * actively harmful: it would read as an authorization control while enforcing nothing, and the next
 * reviewer would stop looking for the real one. The real ones are the matcher named above and
 * {@code DuplicateFilingDetectionService}'s scope filter.
 *
 * <h2>The caller is RESOLVED, never declared</h2>
 * The roles come from {@link RequestIdentityResolver} (token-authoritative; {@code X-User-*} honoured only
 * under dev-local via {@code cms.security.allow-dev-identity-headers}). They are NOT a request parameter,
 * and that is the specific defect this endpoint exists not to repeat: {@code EmailSyndicationApiController}
 * accepted a client-supplied owner and returned every row in the system. Here a client-supplied role would
 * be a client-supplied PRIVACY SCOPE — a caller could type {@code userRole=CEPC_DO} and read another
 * department's complainants. There is no parameter on this method that touches identity.
 *
 * <p>ALL the caller's roles are handed down, never {@code RequestIdentity.getPrimaryRole()}. That field is
 * {@code roles.iterator().next()} over a {@code HashSet}, so for a multi-role officer it names an
 * arbitrary role and can name a DIFFERENT one on another pod — the same officer would see a complainant's
 * history scoped to CEPC on one instance and RBIO on another. For a count that may support a statutory
 * {@code 16(2)(b)} determination, a scope that varies by which pod answered is not acceptable.
 *
 * <h2>The complaint is resolved HERE, and the service is given the entity</h2>
 * The service takes a {@link Complaint} rather than a number because the lookup keys are three of its
 * columns — email, phone and entity code — and resolving it in the controller keeps the service free of a
 * repository lookup it would have to fail silently. An unknown number degrades to the empty shape rather
 * than to a 404 beside a working screen.
 *
 * <p>Note that the complaint is fetched by the OFFICER'S OWN complaint number — the one already on their
 * screen — and the contact details used as lookup keys never leave the server. That is the same resolution
 * {@code PastComplaintService.findPastComplaintsForComplaint} adopted after the staff sidebar was found to
 * be round-tripping a MASKED {@code complainantEmail} as a lookup key, matching nothing and showing "no
 * past complaints" for every complaint ever opened. Unmasking the detail response was the rejected fix;
 * keeping the identifier server-side was the accepted one, and this endpoint follows it.
 *
 * <h2>Always 200</h2>
 * An unknown complaint, an unresolvable identity, a caller with no department scope, an absent table and
 * an internal failure all yield a 200 carrying {@link DuplicateFilingResponse#empty}. A 4xx would be
 * indistinguishable from a malformed request, since {@code GlobalExceptionHandler} maps a bare
 * {@code RuntimeException} to 400, and the screen this sits beside must stay usable.
 *
 * <p>What is NOT degraded away is the SCOPE FILTER. Every degraded path returns NO rows rather than
 * unscoped rows; there is no failure mode in which this endpoint answers with a complaint the caller's
 * department does not own. Failing open on a PII-bearing list is the defect, not the fallback.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistance")
@RequiredArgsConstructor
public class DuplicateFilingController {

    private final DuplicateFilingDetectionService duplicateFilingService;
    private final ComplaintRepository complaintRepository;
    private final RequestIdentityResolver identityResolver;

    /**
     * This feature's {@code §6.2} kill switch, read live.
     *
     * <p>A SEPARATE key from {@code cms.assistance.enabled}: this is the one assistance surface that shows
     * an officer another person's complaints and feeds a statutory determination, so an operator who finds
     * a problem here — a placeholder contact the fan-out guard missed, a scope concern — must be able to
     * stop it immediately without also blinding every officer's rail, and the reverse.
     *
     * <p>Defaults to {@code false}. A feature that degrades SILENTLY cannot be observed failing, so it is
     * turned on per environment by someone who has looked.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null. Same
     * shape, same reason, as {@code AssistanceRailController}.
     */
    @Value("${cms.assistance.duplicate-detection.enabled:false}")
    private boolean duplicateDetectionEnabled;

    /**
     * Whether duplicate detection is switched on, so the frontend can hide the affordance ({@code §6.2}).
     *
     * <p>Shaped {@code {"available": <bool>}} to match {@code AssistanceRailController#status} and
     * {@code ClauseRecommendationController#status}, so the frontend's existing {@code available()}
     * pattern reads it unchanged.
     *
     * <p>Reports the SWITCH and nothing else — no probe, no repository touch, no "does
     * ASSISTANCE_COMPLAINANT_HISTORY exist". A status endpoint that tested liveness would be a second
     * failure path in front of a feature whose entire contract is to fail quietly, and it would report
     * {@code false} for the live shipped state (migration unapplied) even though the correct behaviour
     * there is a working screen with no panel.
     *
     * <p>It reports THIS feature's key only, not its conjunction with {@code cms.assistance.enabled} that
     * the service also enforces. Stated rather than hidden: with the global switch off and this one on,
     * {@code /status} says {@code true} and the read nonetheless returns no signal. The frontend then
     * shows an affordance that reports no duplicates — the SAME thing it shows for a complainant who has
     * none, so not a broken state — and the alternative, duplicating the conjunction here, would be a
     * second copy of an activation rule and the one most likely to drift.
     */
    @GetMapping("/duplicate-filing/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", duplicateDetectionEnabled);
        return ResponseEntity.ok(response);
    }

    /**
     * Earlier complaints by this complainant, and their lifetime filing picture.
     *
     * <p>With the switch off this still returns the documented success shape with
     * {@code available: false} rather than a 403 or an empty body, so a client that never reads
     * {@code /status} — a bookmarked URL, an old bundle, a smoke test — cannot make a disabled feature look
     * broken.
     *
     * <p>The response contains NO complainant name, phone, email, address, account number, subject,
     * description or snippet. See {@link DuplicateFilingResponse.Match} for the field-by-field privacy
     * argument; the point of noting it here is that this controller performs no mapping of its own and
     * therefore cannot add a field — it returns the service's record unchanged, which is what keeps that
     * argument true for the whole endpoint.
     *
     * @param complaintNumber the complaint the officer is working. Deliberately the complaint NUMBER and
     *                        not its id, matching every other assistance endpoint and the frontend's own
     *                        state. Required: without a complaint there is no complainant to look up, and
     *                        an endpoint that accepted an EMAIL here instead would be a complainant-history
     *                        search by contact detail, which is a different and far more dangerous feature.
     */
    @GetMapping("/duplicate-filing")
    public ResponseEntity<DuplicateFilingResponse> detect(
            @RequestParam("complaintNumber") String complaintNumber,
            HttpServletRequest request) {

        if (!duplicateDetectionEnabled) {
            // No service call and no identity resolution at all: "the endpoint must not do work" is the
            // point of the switch, and for a PII-bearing read it also means the rows are not touched.
            return ResponseEntity.ok(DuplicateFilingResponse.empty(complaintNumber));
        }

        try {
            // Null when the identity could not be established. Passed through as-is: the service's scope
            // filter FAILS CLOSED on a null or empty role set, so an unresolved caller receives no rows.
            // Note the contrast with AssistanceRailController, which passes a null role on and still
            // renders its role-independent priors — there is no role-independent half of this feature,
            // because every row it could return belongs to some department.
            RequestIdentity identity = identityResolver.resolve(request);
            Set<String> roles = identity == null ? null : identity.getRoles();

            // Resolved here, not in the service. An unknown or blank number yields null, which the
            // service degrades to the empty shape. A 404 would be wrong — the screen works, and the
            // officer has lost only a panel.
            Complaint complaint = complaintNumber == null || complaintNumber.isBlank()
                    ? null
                    : complaintRepository.findByComplaintNumber(complaintNumber.trim()).orElse(null);

            return ResponseEntity.ok(duplicateFilingService.detect(complaint, roles));
        } catch (Exception e) {
            // The service already absorbs its own failures; this is the outer net that keeps the
            // contract's single success shape true even if identity resolution itself throws. Returns NO
            // rows, because without an identity there is no scope and an unscoped answer is the one
            // outcome this feature must never produce.
            log.warn("Duplicate filing read failed for {}: {}", complaintNumber, e.toString());
            return ResponseEntity.ok(DuplicateFilingResponse.empty(complaintNumber));
        }
    }
}
