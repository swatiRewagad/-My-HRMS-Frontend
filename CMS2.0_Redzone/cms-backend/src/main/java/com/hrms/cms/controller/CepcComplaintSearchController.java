package com.hrms.cms.controller;

import com.hrms.cms.dto.cepc.CepcDashboardResponse;
import com.hrms.cms.dto.cepc.CepcSearchRequest;
import com.hrms.cms.security.CepcIdentityResolver;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.service.CepcComplaintSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The CEPC dashboard grid, KPI cards and tab badges — one request serves all three.
 *
 * <p>Replaces {@code POST /cms-search/api/v1/search/complaints/search} on the standalone search service.
 * That service is gone from the path entirely: this queries MySQL through JPA Criteria, so there is no
 * OpenSearch index to fall out of step with the complaint table.
 *
 * <p><b>The caller is taken from the token, never from the body.</b> The old service read an
 * {@code X-Current-Officer} header that no client ever sent, which left every "assigned to me" filter
 * matching nothing — and would have let anyone read anyone else's worklist by setting it.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/search/complaints")
@RequiredArgsConstructor
public class CepcComplaintSearchController {

    private final CepcComplaintSearchService searchService;
    private final CepcIdentityResolver identity;

    @PostMapping("/search")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> search(
            @RequestBody(required = false) CepcSearchRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {

        CepcComplaintSearchService.Caller caller = new CepcComplaintSearchService.Caller(
                identity.resolveActor(), identity.resolveCepcRole());

        try {
            CepcDashboardResponse data = searchService.search(request, page, size, sort, caller);
            return ResponseEntity.ok(body(true, "OK", data));
        } catch (CepcComplaintSearchService.UnsupportedFilterException e) {
            // A filter the schema cannot answer is the caller's problem to correct, so it comes back as a 400
            // carrying the key the UI translates — not as an empty page, which reads as "no matching
            // complaints" and sends an officer looking for work that was never searched for.
            log.debug("CEPC search rejected: {}", e.getMessageKey());
            return ResponseEntity.badRequest().body(body(false, e.getMessageKey(), null));
        }
    }

    private static Map<String, Object> body(boolean success, String message, Object data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", success);
        out.put("message", message);
        out.put("messageKey", success ? null : message);
        out.put("data", data);
        out.put("timestamp", LocalDateTime.now().toString());
        return out;
    }
}
