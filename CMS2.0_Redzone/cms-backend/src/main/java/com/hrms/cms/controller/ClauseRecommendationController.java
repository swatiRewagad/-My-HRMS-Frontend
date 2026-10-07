package com.hrms.cms.controller;

import com.hrms.cms.dto.ClauseRecommendationResponse;
import com.hrms.cms.dto.ClosureClauseRecommendationResponse.ClauseRecommendation;
import com.hrms.cms.dto.ClosureClauseRecommendationResponse.ClauseRecommendations;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.ClosureClauseRecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The closure-clause recommendation read, and its kill switch (Brief 21 §5.3.2).
 *
 * <h2>Mounted under {@code /api/v1/assistance}, which is where its authorization already is</h2>
 * {@code SecurityConfig} carries a matcher for {@code /api/v1/assistance/**} gated to
 * {@code STAFF_ROLES}, asserted by {@code SecurityConfigEnforcementTest}. Mounting here means this
 * endpoint inherits a control that is tested, rather than needing a new matcher that would have to be
 * added to a file six concurrent sessions are editing. It is also the honest place for it: a ranking
 * mined from other complaints' closures is staff analytics, and the {@code anyRequest().authenticated()}
 * fallback would admit an authenticated CITIZEN.
 *
 * <h2>There is no {@code @PreAuthorize} here, deliberately</h2>
 * {@code @EnableMethodSecurity} is absent from this application, so every such annotation in this
 * codebase is INERT — 31 of them are documented as dead. Adding one would read as a control while
 * enforcing nothing, which is worse than not having it, because the next reader would stop looking for
 * the real one. The real one is the matcher named above.
 *
 * <h2>The caller is RESOLVED, never declared</h2>
 * The role comes from {@link RequestIdentityResolver} (token-authoritative; {@code X-User-*} honoured
 * only under dev-local via {@code cms.security.allow-dev-identity-headers}). It is NOT a request
 * parameter, and that is the specific defect this endpoint exists to avoid repeating: the legacy
 * {@code /api/v1/workflow/closure-clauses} accepts {@code role} as an untrusted query parameter, and the
 * Angular caller computed it client-side with {@code role.includes('OMBUDSMAN')} tested before
 * {@code includes('DEPUTY')} — so a Deputy Ombudsman matched the Ombudsman branch and was handed the
 * award clauses. A clause set is a legal entitlement; it cannot be decided by the browser.
 *
 * <p>ALL the caller's roles are handed down, never {@code RequestIdentity.getPrimaryRole()}. That field
 * is {@code roles.iterator().next()} over a {@code HashSet}, so for a multi-role officer it names an
 * arbitrary role and can name a DIFFERENT one on another pod — the same officer would be offered two
 * different clause sets depending on which instance answered. The service states the rule it applies to
 * the set; see {@code ClosureClauseRecommendationService#permittedClauses}.
 *
 * <h2>The complaint is resolved HERE, and the service is given the entity</h2>
 * {@link ClosureClauseRecommendationService} takes a {@link Complaint}, not a number, because the
 * clause set depends on the complaint's own scheme version and date bounds and because the cohort key is
 * six of its columns. Resolving it in the controller keeps the service free of a repository lookup it
 * would have to fail silently, and an unknown number degrades to the no-complaint case — the configured
 * scheme's clause set, unranked — rather than to a 404 beside a working picker.
 *
 * <h2>Always 200, and the one thing that is not degradation</h2>
 * An unknown complaint, an unresolvable identity, an absent rollup table and an internal failure all
 * yield a 200 carrying the permitted clauses with {@code ranked: false}. The picker this annotates
 * commits a real closure and must stay usable; a 4xx would additionally be indistinguishable from a
 * malformed request, since {@code GlobalExceptionHandler} maps a bare {@code RuntimeException} to 400.
 *
 * <p>What is NOT degraded away is the role filter. An unresolved identity yields an EMPTY clause list,
 * not the unrestricted subset — because the service refuses to ask the access service with a blank role,
 * which would return the unrestricted clauses and present them as this caller's entitlement. Failing
 * open on a list is how a restricted clause reaches a role that may not cite it (§4).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistance")
@RequiredArgsConstructor
public class ClauseRecommendationController {

    private final ClosureClauseRecommendationService recommendationService;
    private final ComplaintRepository complaintRepository;
    private final RequestIdentityResolver identityResolver;

    /**
     * This feature's §6.2 kill switch, read live.
     *
     * <p>A SEPARATE key from {@code cms.assistance.enabled}, which is the deliberate part: the rail is
     * an ambient panel beside the screen, whereas this reorders a control that commits a closure. An
     * operator who needs to stop influencing closure decisions must be able to do that without also
     * blinding the rail, and the reverse.
     *
     * <p>Defaults to {@code false}, following {@code AssistanceRailController}'s reasoning rather than
     * {@code cms.similar-cases.enabled}'s {@code true}: a feature that degrades SILENTLY cannot be
     * observed failing, so it is turned on per environment by someone who has looked, not by a default.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null. Same
     * shape, same reason, as {@code AssistanceRailController} and {@code AaParentComplaintController}.
     */
    @Value("${cms.assistance.clause-recommendation.enabled:false}")
    private boolean clauseRecommendationEnabled;

    /**
     * Whether the recommendation is switched on, so the frontend can hide the affordance (§6.2).
     *
     * <p>Shaped {@code {"available": <bool>}} to match {@code AssistanceRailController#status} and
     * {@code SimilarCasesController#getStatus}, so the frontend's existing {@code available()} pattern
     * reads it unchanged. A SEPARATE endpoint from the rail's rather than a second field on it: these
     * are two switches, and one endpoint reporting both would make a client that read the wrong field
     * hide the wrong thing.
     *
     * <p>Reports the SWITCH and nothing else — no probe, no repository touch, no "does
     * ASSISTANCE_CLAUSE_AFFINITY exist". A status endpoint that tested liveness would be a second
     * failure path in front of a feature whose entire contract is to fail quietly, and it would report
     * {@code false} for the live shipped state (migration unapplied) even though the correct behaviour
     * there is a working, unranked picker.
     *
     * <p>It reports THIS feature's key only, not its conjunction with {@code cms.assistance.enabled}
     * that the service enforces. Stated rather than hidden: with the global switch off and this one on,
     * {@code /status} says {@code true} and the recommendation is nonetheless unranked. The frontend
     * then shows an affordance that reports no history — the SAME thing it shows for an empty rollup, so
     * not a broken state — and the alternative, duplicating the conjunction here, would be a second copy
     * of an activation rule and the one most likely to drift.
     */
    @GetMapping("/clause-recommendation/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", clauseRecommendationEnabled);
        return ResponseEntity.ok(response);
    }

    /**
     * The clauses this officer may cite for this complaint, ranked where history supports it.
     *
     * <p>With the switch off this still returns the PERMITTED CLAUSE LIST, unranked — not an empty
     * response and not a 403. The reason is that the response's contract is "every clause the role may
     * cite, best-evidenced first if there is evidence", and the switch governs the EVIDENCE, not the
     * list. A client that never reads {@code /status} — a bookmarked URL, an old bundle, a smoke test —
     * therefore still receives the single documented success shape with {@code ranked: false}, and
     * cannot make a disabled feature look broken. The frontend hides the annotations from
     * {@code /status}; this is the server making sure it does not have to.
     *
     * @param complaintNumber optional. The picker is also opened with no complaint in scope, in which
     *                        case there is no cohort key and the answer is the configured scheme's
     *                        clause set, unranked. Deliberately the complaint NUMBER and not its id,
     *                        matching every other assistance endpoint and the frontend's own state
     */
    @GetMapping("/clause-recommendation")
    public ResponseEntity<ClauseRecommendationResponse> recommend(
            @RequestParam(value = "complaintNumber", required = false) String complaintNumber,
            HttpServletRequest request) {

        try {
            // Null when the identity could not be established. Passed through as-is: the service asks
            // the access service with a BLANK role, which refuses every restricted clause, so an
            // unresolved caller receives the unrestricted subset and never a restricted one. Note the
            // contrast with AssistanceRailController, which passes a null role on and still renders its
            // role-independent priors — there is no role-independent half of this feature.
            RequestIdentity identity = identityResolver.resolve(request);
            Set<String> roles = identity == null ? null : identity.getRoles();

            // Resolved here, not in the service. An unknown or absent number yields null, which the
            // service treats as "no complaint in scope": the configured scheme's clause set, unranked.
            // A 404 would be wrong — the picker works, and the officer has lost only an ordering.
            Complaint complaint = complaintNumber == null || complaintNumber.isBlank()
                    ? null
                    : complaintRepository.findByComplaintNumber(complaintNumber).orElse(null);

            return ResponseEntity.ok(toWire(complaintNumber,
                    recommendationService.recommend(complaint, roles)));
        } catch (Exception e) {
            // The service already absorbs its own failures; this is the outer net that keeps the
            // contract's single success shape true even if identity resolution itself throws. Returns
            // NO clauses, because without an identity there is no permitted set to return and inventing
            // one would be the §4 failure this endpoint was written to avoid.
            log.warn("Clause recommendation read failed for {}: {}", complaintNumber, e.toString());
            return ResponseEntity.ok(ClauseRecommendationResponse.empty(complaintNumber));
        }
    }

    /**
     * Maps the service's answer onto the wire contract.
     *
     * <h3>Why the two shapes are not one shape</h3>
     * The service's {@link ClauseRecommendations} uses primitive {@code long} counts with 0 meaning
     * "no history", because inside the service every clause has a count and 0 is a true count. The WIRE
     * contract uses boxed {@code Long} with {@code null} meaning "unranked", because absent and zero are
     * different claims to a client: "cited 0 times in 815 comparable closures" is a statement, and
     * "there is no comparable cohort" is not. Collapsing them would make an unranked picker
     * indistinguishable from a cohort in which a clause was never cited.
     *
     * <h3>{@code specificity} is deliberately NOT carried to the wire</h3>
     * The service computes it and this mapping drops it. It is diagnostics — how many of the five
     * optional dimensions were specific rather than wildcarded — and on the measured data it is 0 on
     * every row that can exist today, because ground is 0%-populated, category 7% and entity type
     * resolves on 1 of 877. Shipping a field that is always 0 invites a client to render it, and a
     * number an officer cannot act on does not belong beside a statutory clause. Recorded here rather
     * than removed from the service, because the moment those columns populate it becomes the honest
     * answer to "why this order" and the service should not have to grow it back.
     */
    private ClauseRecommendationResponse toWire(String complaintNumber, ClauseRecommendations result) {
        List<ClauseRecommendationResponse.Recommendation> clauses =
                new ArrayList<>(result.clauses().size());

        for (ClauseRecommendation clause : result.clauses()) {
            // A clause is RANKED iff the cohort held a row for it, which the service signals with a
            // non-zero denominator — cohortTotal is 0 exactly on the entries it built as unranked. Not
            // keyed on occurrences, which is legitimately 0 for a permitted clause inside a real cohort.
            if (clause.cohortTotal() == 0L) {
                clauses.add(ClauseRecommendationResponse.Recommendation.unranked(
                        clause.clauseCode(), clause.label(), clause.labelKey()));
                continue;
            }
            clauses.add(new ClauseRecommendationResponse.Recommendation(
                    clause.clauseCode(), clause.label(), clause.labelKey(),
                    clause.occurrences(), clause.cohortTotal(), clause.recommended()));
        }

        if (!result.ranked()) {
            // Collapses the five degraded states into the one documented shape. See the DTO.
            return ClauseRecommendationResponse.unranked(complaintNumber, clauses);
        }
        return new ClauseRecommendationResponse(
                complaintNumber, true, result.cohortTotal(), List.copyOf(clauses));
    }
}
