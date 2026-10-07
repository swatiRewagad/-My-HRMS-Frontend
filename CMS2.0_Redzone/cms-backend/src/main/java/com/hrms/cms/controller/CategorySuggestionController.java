package com.hrms.cms.controller;

import com.hrms.cms.dto.CategorySuggestionResponse;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.CategorySuggestionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The auto-category suggestion read, and its kill switch.
 *
 * <h2>Mounted under {@code /api/v1/assistance}, which is where its authorization already is</h2>
 * {@code SecurityConfig} carries a matcher for {@code /api/v1/assistance/**} gated to
 * {@code ASSISTANCE_ROLES}, asserted by {@code SecurityConfigEnforcementTest}. Mounting here means this
 * endpoint inherits a control that is tested, rather than needing a new matcher in a file six concurrent
 * sessions are editing — and it also inherits the {@code assistance-requests-per-minute} rate-limit
 * bucket, which matters for this endpoint more than for its siblings: it is the only assistance read a
 * UI would plausibly call on a debounce while someone types.
 *
 * <p>It is also the honest place for it. A ranking mined from other complainants' categorised text is
 * staff analytics, and the {@code anyRequest().authenticated()} fallback would admit an authenticated
 * CITIZEN. That is a live question for this feature and not a theoretical one: the most valuable place
 * to show a category hint is the citizen's own filing form, and §4 does not permit it there on this
 * mounting. See "WHAT THIS DELIBERATELY DOES NOT DO" below.
 *
 * <h2>There is no {@code @PreAuthorize} here, deliberately</h2>
 * {@code @EnableMethodSecurity} is absent from this application, so every such annotation in this
 * codebase is INERT — 31 of them are documented as dead. Adding one would read as a control while
 * enforcing nothing, which is worse than not having it, because the next reader would stop looking for
 * the real one. The real one is the matcher named above.
 *
 * <h2>The caller is RESOLVED, never declared — and then not used</h2>
 * The identity comes from {@link RequestIdentityResolver} (token-authoritative; {@code X-User-*} honoured
 * only under dev-local). It is NOT a request parameter. The prior is role-blind — a word's association
 * with a category is a count, not an entitlement — so there is nothing to filter on, and
 * {@code CategorySuggestionService} takes no role parameter at all. The resolution happens anyway, for
 * two reasons: an unresolvable identity is logged rather than served silently, and the whole role Set is
 * what would be passed if a role-dependent rule were ever added.
 *
 * <p>If such a rule is added, it must take the whole {@link RequestIdentity#getRoles()} Set and NEVER
 * {@code getPrimaryRole()}. That field is {@code roles.iterator().next()} over a {@code HashSet}, so for
 * a multi-role officer it names an arbitrary role and can name a DIFFERENT one on another pod.
 *
 * <h2>Always 200, and the empty signal is a success shape</h2>
 * An unknown complaint number, blank text, an unresolvable identity, an absent rollup table and an
 * internal failure all yield a 200 carrying {@code available: false} and no suggestions. The form this
 * annotates files a real complaint and must stay usable; a 4xx would additionally be indistinguishable
 * from a malformed request, since {@code GlobalExceptionHandler} maps a bare {@code RuntimeException} to
 * 400.
 *
 * <h2>WHAT THIS DELIBERATELY DOES NOT DO</h2>
 * <ul>
 *   <li><b>It does not write {@code category_id}.</b> This is a read endpoint. The whole value of the
 *       feature is that accepting suggestions populates a column three other assistance rollups are
 *       starved of — but the acceptance is an officer's act through the existing update path, not a side
 *       effect of asking for a hint. An endpoint that classified on read would populate the column with
 *       its own guesses and then learn from them, which is a feedback loop that would converge on the
 *       majority class within a few cycles and be indistinguishable from signal.
 *   <li><b>It is not reachable by a CITIZEN.</b> The {@code /api/v1/assistance/**} matcher is gated to
 *       staff roles. A citizen filing form is where a category hint is most useful and a separate
 *       mounting would be needed; that is a §4 decision about showing a citizen a statistic derived from
 *       other complainants' text, and it is not taken here.
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistance")
@RequiredArgsConstructor
public class CategorySuggestionController {

    /**
     * The longest raw {@code text} this endpoint will tokenise.
     *
     * <p>§6.2 wants a declared bound on every input as well as every query. {@code description} is a
     * {@code TEXT} column, so a caller can post a megabyte; the tokenizer would then split it and
     * {@code AssistanceTextTokenizer.capped} would sort the result, which is work done before any cap
     * applies. 8,000 characters is several times the longest complaint in the register (MEASURED: mean
     * subject 41 characters, mean description 60) and is truncated rather than rejected, because
     * rejecting would be a 4xx on a contract that promises 200.
     *
     * <p>Truncation is at a character boundary and may cut a word in half. That is acceptable and
     * stated: the half-word fails to match any stored token and the suggestion is computed from the rest,
     * which is a marginally weaker answer and not a wrong one.
     */
    static final int MAX_TEXT_LENGTH = 8_000;

    private final CategorySuggestionService suggestionService;
    private final ComplaintRepository complaintRepository;
    private final RequestIdentityResolver identityResolver;

    /**
     * Whether the suggestion is switched on, so the frontend can hide the affordance (§6.2).
     *
     * <p>Shaped {@code {"available": <bool>}} to match {@code AssistanceRailController#status} and
     * {@code ClauseRecommendationController#status}, so the frontend's existing {@code available()}
     * pattern reads it unchanged. A SEPARATE endpoint from the rail's rather than a second field on it:
     * these are independent switches, and one endpoint reporting both would make a client that read the
     * wrong field hide the wrong thing.
     *
     * <p>Reports the SWITCHES and nothing else — no probe, no repository touch, no "does
     * ASSISTANCE_CATEGORY_PRIOR exist". A status endpoint that tested liveness would be a second failure
     * path in front of a feature whose entire contract is to fail quietly, and it would report
     * {@code false} for the live shipped state (migration unapplied) even though the correct behaviour
     * there is a working form with no hint.
     *
     * <p>Unlike the clause recommendation's status, this one reports the CONJUNCTION of both keys, via
     * {@code CategorySuggestionService.enabled()}. The difference is deliberate: that endpoint returns a
     * usable payload (the permitted clause list) with the switch off, so a client that hid the wrong
     * thing still had a working picker. This one returns nothing usable when either switch is off, so a
     * {@code /status} saying {@code true} while the read is silent would be a client rendering an
     * affordance that can never populate. One activation rule, read from one place.
     */
    @GetMapping("/category-suggestion/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", suggestionService.enabled());
        return ResponseEntity.ok(response);
    }

    /**
     * The categories this complaint's text points at, with the counts behind each.
     *
     * <h3>{@code complaintNumber} OR {@code text}, and {@code complaintNumber} wins</h3>
     * Two callers, two shapes. An officer triaging an already-filed complaint has a number and should
     * not have to re-send the text; a filing form has text and no number yet. When BOTH arrive the
     * NUMBER wins and the text is ignored — deliberately, because the stored text is the authoritative
     * record of what was filed and a caller that sent both has, by definition, sent something that could
     * disagree with it. Silently preferring the request body would let a client influence a statistic
     * about a complaint it was only reading.
     *
     * <h3>An unknown number degrades to the empty signal, not to a 404</h3>
     * Deliberately the complaint NUMBER and not its id, matching every other assistance endpoint and the
     * frontend's own state. An unknown one yields no text and therefore no suggestion — a 404 beside a
     * working form would be wrong, and it would also be an existence oracle for complaint numbers on an
     * endpoint that does not otherwise need one.
     *
     * <h3>No complaint TEXT is echoed back</h3>
     * The response carries the matched TOKENS, which are words that appear in three or more labelled
     * complaints and contain no digits, and never a span of the complaint's prose. So a caller who
     * guessed a complaint number learns which of a small shared vocabulary that complaint contains — and
     * nothing that could identify anyone. Stated because the obvious implementation, returning the
     * matched phrase for highlighting, would be a PII leak through an endpoint whose payload otherwise
     * holds none.
     */
    @GetMapping("/category-suggestion")
    public ResponseEntity<CategorySuggestionResponse> suggest(
            @RequestParam(value = "complaintNumber", required = false) String complaintNumber,
            @RequestParam(value = "text", required = false) String text,
            HttpServletRequest request) {

        try {
            // Resolved, never declared. Not passed to the service — the prior is role-blind — but
            // resolved so an unresolvable identity is visible in the log rather than served silently,
            // and so the Set is in hand if a role-dependent rule is ever added. NEVER getPrimaryRole().
            RequestIdentity identity = identityResolver.resolve(request);
            Set<String> roles = identity == null ? null : identity.getRoles();
            if (roles == null || roles.isEmpty()) {
                log.debug("Category suggestion served to an unresolved identity; the prior is "
                        + "role-blind so no filtering is lost.");
            }

            if (complaintNumber != null && !complaintNumber.isBlank()) {
                // The number wins over any supplied text. An unknown number yields null and therefore
                // the empty signal.
                Complaint complaint = complaintRepository
                        .findByComplaintNumber(complaintNumber).orElse(null);
                if (complaint == null) {
                    return ResponseEntity.ok(CategorySuggestionResponse.empty(complaintNumber));
                }
                return ResponseEntity.ok(suggestionService.suggest(
                        complaintNumber, complaint.getSubject(), complaint.getDescription()));
            }

            if (text == null || text.isBlank()) {
                return ResponseEntity.ok(CategorySuggestionResponse.empty(null));
            }
            return ResponseEntity.ok(suggestionService.suggest(null, truncate(text)));
        } catch (Exception e) {
            // The service already absorbs its own failures; this is the outer net that keeps the
            // contract's single success shape true even if identity resolution or the complaint read
            // throws. NOTE: e.toString() and not the text — a stack trace carrying a complainant's
            // prose into the log would be the one PII leak this feature could plausibly introduce.
            log.warn("Category suggestion read failed for {}: {}", complaintNumber, e.toString());
            return ResponseEntity.ok(CategorySuggestionResponse.empty(complaintNumber));
        }
    }

    /** Caps the raw text at {@link #MAX_TEXT_LENGTH}; see that constant for why truncate and not reject. */
    private static String truncate(String text) {
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
    }
}
