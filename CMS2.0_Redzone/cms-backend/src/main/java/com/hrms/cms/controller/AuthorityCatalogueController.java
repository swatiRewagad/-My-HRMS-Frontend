package com.hrms.cms.controller;

import com.hrms.cms.security.CmsAuthority;
import com.hrms.cms.security.CmsPrincipalResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes the authorities this build enforces, for the SSO to map roles against (UAM contract).
 *
 * <h2>Why an endpoint and not a document</h2>
 * User administration is delegated: the SSO holds users, roles and the role→authority mapping. That makes
 * the authority list an integration contract between two systems, and a contract kept in a document drifts
 * the moment someone adds an authority without updating it. The consequence of drift is asymmetric — an
 * authority that exists here but is mapped nowhere refuses every caller, which is at least visible, but an
 * administrator working from a stale document cannot tell which of the two they are looking at.
 *
 * <p>So the running build is the source of truth and says what it enforces.
 *
 * <h2>Why this is readable by any signed-in caller</h2>
 * It returns no data about users, no mappings, and no information about who holds what — only the names and
 * descriptions of capabilities, which are already visible in the API's behaviour. Restricting it would mean
 * the administrator configuring the SSO needs an application authority before they can discover which
 * authorities exist, which is circular.
 */
@RestController
@RequestMapping("/api/v1/authorities")
@RequiredArgsConstructor
public class AuthorityCatalogueController {

    private final CmsPrincipalResolver principalResolver;

    /** Every authority this build enforces, with the principal type it applies to. */
    @GetMapping("/catalogue")
    public ResponseEntity<Map<String, Object>> catalogue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authorities", CmsAuthority.catalogue());
        data.put("count", CmsAuthority.values().length);
        // Named so an administrator knows which claim to populate rather than guessing.
        data.put("expectedClaim", "authorities");
        data.put("entityScopeClaim", "entity_code");
        return envelope("Authorities enforced by this build", data);
    }

    /**
     * What the CALLER currently holds, so a UI can hide what it cannot do.
     *
     * <p>Hiding is a convenience, never the control — every capability is enforced server-side by
     * {@code @RequiresAuthority}. UST631 makes this explicit: the Final Decision options are hidden for a
     * Reviewer without closure authority, AND direct URL access is refused. This endpoint serves the first
     * half; the aspect serves the second.
     */
    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> mine() {
        CmsPrincipalResolver.Principal principal = principalResolver.resolve();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", principal.username());
        data.put("principalType", principal.type().name());
        // Present for a Regulated Entity user and null for RBI staff. The UI uses it to label whose data
        // is on screen; the server uses it to scope the query.
        data.put("entityCode", principal.entityCode());
        data.put("authorities", principal.authorities());
        return envelope("Authorities granted to the signed-in user", data);
    }

    private ResponseEntity<Map<String, Object>> envelope(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
