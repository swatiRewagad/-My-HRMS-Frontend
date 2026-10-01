package com.hrms.cms.controller;

import com.hrms.cms.entity.ReassignmentClarification;
import com.hrms.cms.entity.ReassignmentRequest;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.ReassignmentService;
import com.hrms.cms.service.ReassignmentService.BulkItem;
import com.hrms.cms.service.ReassignmentService.BulkResult;
import com.hrms.cms.service.ReassignmentService.Candidate;
import com.hrms.cms.service.WorkloadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * RE nodal-officer reassignment (UST838–UST843).
 *
 * <p>Identity is resolved server-side on every call via {@link RequestIdentityResolver}; the entity
 * an RE caller may act on comes from their token/headers, never from a parameter. Authorisation,
 * workload and visibility decisions all happen in the service layer — the browser is never trusted to
 * filter, because a filtered list is not an access control.
 *
 * <p>Response envelope follows the established convention here: a {@code Map<String,Object>} with
 * {@code success} plus payload keys, built inline. There is no DTO/mapper layer in this codebase.
 */
@RestController
@RequestMapping("/api/v1/re-portal/reassignment")
@RequiredArgsConstructor
@Slf4j
public class ReassignmentController {

    private final ReassignmentService reassignmentService;
    private final WorkloadService workloadService;
    private final RequestIdentityResolver identityResolver;

    // ═══════════════════════════════════════════════════════════════
    // Candidates and workload (UST838, UST841)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/candidates")
    public ResponseEntity<Map<String, Object>> candidates(
            @RequestParam(required = false) String entityCode,
            @RequestParam(required = false) Long recordId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            List<Candidate> candidates =
                    reassignmentService.findCandidates(identity, entityCode, recordId, role, search);

            List<Map<String, Object>> items = new ArrayList<>();
            for (Candidate c : candidates) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("userId", c.getUserId());
                item.put("displayName", c.getDisplayName());
                item.put("email", c.getEmail());
                item.put("designation", c.getDesignation());
                item.put("reRole", c.getReRole());
                item.put("territory", c.getTerritory());
                item.put("workload", c.getWorkload());
                items.add(item);
            }
            return ResponseEntity.ok(Map.of("success", true, "count", items.size(),
                                            "candidates", items));
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    /**
     * Workload per officer for the PNO dashboard (UST838).
     *
     * <p>Served from the same {@link WorkloadService} method the candidate list uses, which is what
     * makes the two surfaces agree rather than merely intending to.
     */
    @GetMapping("/workload")
    public ResponseEntity<Map<String, Object>> workload(
            @RequestParam(required = false) String entityCode,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            String scope = reassignmentService.resolveScope(identity, entityCode);
            Map<String, Integer> workloads = workloadService.workloadForEntity(scope);

            List<Map<String, Object>> items = new ArrayList<>();
            int total = 0;
            for (Map.Entry<String, Integer> entry : workloads.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("userId", entry.getKey());
                item.put("workload", entry.getValue());
                items.add(item);
                total += entry.getValue();
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("entityCode", scope);
            response.put("officers", items);
            response.put("totalActiveRecords", total);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Requests (UST840, UST842)
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> raise(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            ReassignmentService.RequestOutcome outcome = reassignmentService.raiseRequest(
                    identity,
                    str(body.get("entityCode")),
                    asLong(body.get("recordId")),
                    asLong(body.get("expectedVersion")),
                    str(body.get("toUserId")),
                    str(body.get("reason")));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("applied", outcome.isApplied());
            response.put("request", render(outcome.getRequest()));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ReassignmentService.ConflictingStateException e) {
            return conflict(e);
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    /** UST842 — the caller's own requests, scoped by their resolved user id. */
    @GetMapping("/requests/mine")
    public ResponseEntity<Map<String, Object>> myRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        Page<ReassignmentRequest> results =
                reassignmentService.myRequests(identity, status, page, size);
        return ResponseEntity.ok(pageEnvelope(results));
    }

    /** UST843 — pending approvals for the caller's entity. PNO only, enforced server-side. */
    @GetMapping("/requests/pending")
    public ResponseEntity<Map<String, Object>> pending(
            @RequestParam(required = false) String entityCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            return ResponseEntity.ok(pageEnvelope(
                    reassignmentService.pendingApprovals(identity, entityCode, page, size)));
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/requests/{requestId}/clarifications")
    public ResponseEntity<Map<String, Object>> addClarification(
            @PathVariable Long requestId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            ReassignmentClarification saved = reassignmentService.addClarification(
                    identity, str(body.get("entityCode")), requestId, str(body.get("note")));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("success", true, "clarification", render(saved)));
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/requests/{requestId}/clarifications")
    public ResponseEntity<Map<String, Object>> clarifications(
            @PathVariable Long requestId,
            @RequestParam(required = false) String entityCode,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ReassignmentClarification c :
                    reassignmentService.getClarifications(identity, entityCode, requestId)) {
                items.add(render(c));
            }
            return ResponseEntity.ok(Map.of("success", true, "count", items.size(),
                                            "clarifications", items));
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/requests/{requestId}/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(@PathVariable Long requestId,
                                                        @RequestBody(required = false) Map<String, Object> body,
                                                        HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        try {
            ReassignmentRequest withdrawn = reassignmentService.withdraw(
                    identity, str(safeBody.get("entityCode")), requestId, str(safeBody.get("note")));
            return ResponseEntity.ok(Map.of("success", true, "request", render(withdrawn)));
        } catch (ReassignmentService.ConflictingStateException e) {
            return conflict(e);
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Bulk actions (UST839, UST843)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Bulk approve or reject. Returns 200 with a per-item breakdown even when some items failed:
     * the action genuinely partially succeeded, and a 4xx would imply nothing took effect. A
     * conflict on one record is reported in {@code failed}, not as the status of the whole call.
     */
    @PostMapping("/requests/decide")
    public ResponseEntity<Map<String, Object>> decide(@RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            boolean approve = asBoolean(body.get("approve"));
            BulkResult result = reassignmentService.decideBulk(
                    identity, str(body.get("entityCode")), items(body), approve,
                    str(body.get("comment")));
            return ResponseEntity.ok(renderBulk(result));
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    /** Direct bulk reassign by a PNO. Same partial-success semantics as {@link #decide}. */
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulkReassign(@RequestBody Map<String, Object> body,
                                                            HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            BulkResult result = reassignmentService.reassignBulk(
                    identity, str(body.get("entityCode")), items(body),
                    str(body.get("toUserId")), str(body.get("reason")));
            return ResponseEntity.ok(renderBulk(result));
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Rendering and helpers
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<BulkItem> items(Map<String, Object> body) {
        List<BulkItem> items = new ArrayList<>();
        Object raw = body.get("items");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                BulkItem item = new BulkItem();
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    item.setRequestId(asLong(map.get("requestId")));
                    item.setRecordId(asLong(map.get("recordId")));
                    item.setExpectedVersion(asLong(map.get("expectedVersion")));
                } else if (o != null) {
                    // Accept a bare list of ids as a convenience; no version protection then.
                    item.setRequestId(asLong(o));
                    item.setRecordId(asLong(o));
                }
                items.add(item);
            }
        }
        return items;
    }

    private Map<String, Object> renderBulk(BulkResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("succeededCount", result.getSucceeded().size());
        response.put("failedCount", result.getFailed().size());
        response.put("succeeded", result.getSucceeded());
        response.put("failed", result.getFailed());
        return response;
    }

    private Map<String, Object> pageEnvelope(Page<ReassignmentRequest> page) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ReassignmentRequest r : page.getContent()) {
            items.add(render(r));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("requests", items);
        response.put("totalElements", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        return response;
    }

    private Map<String, Object> render(ReassignmentRequest r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", r.getId());
        item.put("recordId", r.getNodalOfficerRecordId());
        item.put("complaintNumber", r.getComplaintNumber());
        item.put("entityCode", r.getEntityCode());
        item.put("fromUserId", r.getFromUserId());
        item.put("fromUserName", r.getFromUserName());
        item.put("toUserId", r.getToUserId());
        item.put("toUserName", r.getToUserName());
        item.put("reason", r.getReason());
        item.put("status", r.getStatus());
        item.put("requestedBy", r.getRequestedBy());
        item.put("requestedByName", r.getRequestedByName());
        item.put("requestedAt", r.getRequestedAt() == null ? null : r.getRequestedAt().toString());
        item.put("decidedBy", r.getDecidedBy());
        item.put("decidedAt", r.getDecidedAt() == null ? null : r.getDecidedAt().toString());
        item.put("decisionComment", r.getDecisionComment());
        item.put("toUserWorkloadAtRequest", r.getToUserWorkloadAtRequest());
        return item;
    }

    private Map<String, Object> render(ReassignmentClarification c) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", c.getId());
        item.put("requestId", c.getReassignmentRequestId());
        item.put("note", c.getNote());
        item.put("addedBy", c.getAddedBy());
        item.put("addedByName", c.getAddedByName());
        item.put("addedBySide", c.getAddedBySide());
        item.put("addedAt", c.getAddedAt() == null ? null : c.getAddedAt().toString());
        return item;
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean asBoolean(Object value) {
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private ResponseEntity<Map<String, Object>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "messageKey", "re.reassign.error.unauthenticated"));
    }

    private ResponseEntity<Map<String, Object>> forbidden(Exception e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "messageKey", keyOf(e, "re.reassign.error.not_permitted")));
    }

    private ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("success", false, "messageKey", keyOf(e, "re.reassign.error.invalid")));
    }

    private ResponseEntity<Map<String, Object>> conflict(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("success", false, "messageKey", keyOf(e, "re.reassign.error.conflict")));
    }

    private ResponseEntity<Map<String, Object>> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "messageKey", "re.reassign.error.not_found"));
    }

    /**
     * Service-layer messages are already translation keys, so they pass through. Anything that is
     * not a key (a JPA message, say) is replaced by the generic key rather than leaked to the client.
     */
    private String keyOf(Exception e, String fallback) {
        String message = e.getMessage();
        if (message != null && message.startsWith("re.reassign.")) {
            return message;
        }
        return fallback;
    }
}
