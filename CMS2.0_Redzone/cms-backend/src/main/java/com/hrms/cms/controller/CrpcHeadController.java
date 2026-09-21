package com.hrms.cms.controller;

import com.hrms.cms.entity.InterOfficeTransfer;
import com.hrms.cms.entity.OfficeThresholdConfig;
import com.hrms.cms.service.BulkReassignService;
import com.hrms.cms.service.InterOfficeTransferService;
import com.hrms.cms.service.OfficeRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/crpc/head")
@PreAuthorize("hasAnyRole('CRPC_HEAD', 'ADMIN')")
@RequiredArgsConstructor
public class CrpcHeadController {

    private final InterOfficeTransferService transferService;
    private final OfficeRoutingService officeRoutingService;
    private final BulkReassignService bulkReassignService;

    @GetMapping("/transfers/pending")
    public ResponseEntity<List<InterOfficeTransfer>> getPendingTransfers() {
        return ResponseEntity.ok(transferService.getPendingTransfers());
    }

    @GetMapping("/transfers/pending/count")
    public ResponseEntity<Map<String, Object>> getPendingCount() {
        return ResponseEntity.ok(Map.of("count", transferService.getPendingCount()));
    }

    /**
     * Approves a transfer, optionally redirecting it to a different office.
     *
     * <p>{@code overrideToOffice} is accepted as EITHER a query parameter or a body field. It was
     * query-only, while {@code ops-head.component.ts} POSTs a JSON body — so the override silently never
     * arrived, and the reject endpoint below (whose comment was mandatory) answered HTTP 400 on every call
     * the UI made.
     */
    @PostMapping("/transfers/{id}/approve")
    public ResponseEntity<InterOfficeTransfer> approveTransfer(
            @PathVariable Long id,
            @RequestParam(required = false) String overrideToOffice,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String override = firstNonBlank(overrideToOffice,
                body == null ? null : body.get("overrideToOffice"),
                body == null ? null : body.get("toOffice"));
        return ResponseEntity.ok(transferService.approveTransfer(id, actorOf(jwt, body), override));
    }

    /**
     * Rejects a transfer with a mandatory comment (UST526, 567).
     *
     * <p>The comment is accepted from the query string or the body, for the reason above. It is no longer
     * declared {@code required}: a required {@code @RequestParam} produced a 400 with no usable message for
     * the UI's body-shaped request, and a BLANK query value satisfied it anyway. The service now enforces
     * non-blankness, so "mandatory" means mandatory rather than merely present.
     */
    @PostMapping("/transfers/{id}/reject")
    public ResponseEntity<InterOfficeTransfer> rejectTransfer(
            @PathVariable Long id,
            @RequestParam(required = false) String comment,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String rejectionComment = firstNonBlank(comment,
                body == null ? null : body.get("comment"),
                body == null ? null : body.get("rejectionComment"),
                body == null ? null : body.get("remarks"));
        return ResponseEntity.ok(transferService.rejectTransfer(id, actorOf(jwt, body), rejectionComment));
    }

    @PostMapping("/transfers/request")
    public ResponseEntity<InterOfficeTransfer> requestTransfer(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(transferService.requestTransfer(
                request.get("complaintNumber"),
                request.get("fromOffice"),
                request.get("toOffice"),
                request.get("transferType"),
                request.get("reason"),
                actorOf(jwt, request),
                request.get("language")
        ));
    }

    /**
     * The acting user.
     *
     * <p>{@code jwt.getSubject()} alone throws an NPE — surfacing as a 400 — on a dev-identity-header
     * request, which is the same defect {@code updateThreshold} was already fixed for. The JWT remains
     * authoritative when present; the body value is a fallback for the header-identity path only.
     */
    private static String actorOf(Jwt jwt, Map<String, String> body) {
        if (jwt != null && jwt.getSubject() != null) {
            return jwt.getSubject();
        }
        String fromBody = body == null ? null
                : firstNonBlank(body.get("approvedBy"), body.get("actor"), body.get("rejectedBy"));
        return fromBody == null ? "SYSTEM" : fromBody;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    @GetMapping("/transfers/history/{complaintNumber}")
    public ResponseEntity<List<InterOfficeTransfer>> getTransferHistory(@PathVariable String complaintNumber) {
        return ResponseEntity.ok(transferService.getTransferHistory(complaintNumber));
    }

    // Office threshold management
    @GetMapping("/office-thresholds")
    public ResponseEntity<List<OfficeThresholdConfig>> getOfficeThresholds() {
        return ResponseEntity.ok(officeRoutingService.getAllOfficeConfigs());
    }

    /**
     * Changes an office's capacity.
     *
     * <p>The actor is resolved without dereferencing the JWT directly. Previously this called
     * {@code jwt.getSubject()} on an {@code @AuthenticationPrincipal} that is null whenever the
     * request carries dev identity headers instead of a bearer token, which surfaced as
     * {@code 400 Cannot invoke "Jwt.getSubject()" because "jwt" is null} — an NPE presented as a
     * client error, so an operator could not tell a missing token from a bad threshold value.
     *
     * <p>The JWT subject is still PREFERRED over the header, so a real token remains authoritative
     * for the audit trail and a caller cannot attribute a capacity change to someone else by
     * setting a header. Capacity is an operational limit, not a statutory determination, so falling
     * back to the dev-identity header is acceptable here; it is recorded as-is rather than being
     * silently replaced with a literal like "admin".
     */
    @PutMapping("/office-thresholds/{officeId}")
    public ResponseEntity<Map<String, Object>> updateThreshold(
            @PathVariable String officeId,
            @RequestParam int threshold,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String headerUser) {
        String actor = jwt != null ? jwt.getSubject()
                : (headerUser != null && !headerUser.isBlank() ? headerUser : "unknown");
        officeRoutingService.updateThreshold(officeId, threshold, actor);
        return ResponseEntity.ok(Map.of("status", "updated", "officeId", officeId,
                "newThreshold", threshold, "updatedBy", actor));
    }

    @PostMapping("/office-thresholds/reset")
    public ResponseEntity<Map<String, Object>> resetCounters(@RequestParam(defaultValue = "RBIO") String department) {
        officeRoutingService.resetAllCounters(department);
        return ResponseEntity.ok(Map.of("status", "reset", "department", department));
    }

    /**
     * Reassigns several complaints to one officer.
     *
     * <p><b>This was a stub that persisted nothing.</b> It read {@code complaintIds}, counted them, and
     * returned {@code {"status":"reassigned","count":N}} without touching a repository — so the ops-head
     * screen reported N complaints reassigned and none of them moved. It also read {@code targetUser} while
     * {@code ops-head.component.ts} sends {@code assignTo}, so even the echoed name was null.
     *
     * <p>Now each complaint is loaded, reassigned and timelined individually, and the response reports what
     * actually happened per complaint rather than a count of what was asked. A complaint that cannot be
     * reassigned is named in {@code failed} instead of being silently included in a success count.
     */
    @PostMapping("/bulk-reassign")
    public ResponseEntity<Map<String, Object>> bulkReassign(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt) {
        @SuppressWarnings("unchecked")
        List<String> complaintNumbers = (List<String>) request.get("complaintIds");
        // assignTo is what the ops-head screen actually sends; targetUser is the documented name. Both are
        // read so neither client is silently ignored.
        String targetUser = firstNonBlank(
                stringOf(request.get("assignTo")),
                stringOf(request.get("targetUser")),
                stringOf(request.get("targetUserId")));

        if (complaintNumbers == null || complaintNumbers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "complaintIds is required and must not be empty");
        }
        if (targetUser == null) {
            // Refused rather than defaulted. A bulk reassignment with no target would previously report
            // success for every complaint while assigning none of them to nobody.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A target officer is required (send assignTo)");
        }

        String actor = actorOf(jwt, null);
        List<String> reassigned = new java.util.ArrayList<>();
        Map<String, String> failed = new java.util.LinkedHashMap<>();

        for (String complaintNumber : complaintNumbers) {
            try {
                bulkReassignService.reassign(complaintNumber, targetUser, actor);
                reassigned.add(complaintNumber);
            } catch (Exception e) {
                // Collected, not thrown: one unreassignable complaint must not silently abandon the rest of
                // the batch, and the caller needs to know exactly which ones did not move.
                failed.put(complaintNumber, e.getMessage());
            }
        }

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("status", failed.isEmpty() ? "reassigned" : "partially_reassigned");
        response.put("requested", complaintNumbers.size());
        response.put("count", reassigned.size());
        response.put("reassigned", reassigned);
        response.put("failed", failed);
        response.put("targetUser", targetUser);
        return ResponseEntity.ok(response);
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // Reopen closed complaint
    @PostMapping("/reopen/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> reopenComplaint(
            @PathVariable String complaintNumber,
            @RequestParam String reason,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of("status", "reopened", "complaintNumber", complaintNumber, "by", jwt.getSubject()));
    }
}
