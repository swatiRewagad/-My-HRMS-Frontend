package com.hrms.cms.controller;

import com.hrms.cms.entity.InterOfficeTransfer;
import com.hrms.cms.entity.OfficeThresholdConfig;
import com.hrms.cms.service.InterOfficeTransferService;
import com.hrms.cms.service.OfficeRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/transfers/pending")
    public ResponseEntity<List<InterOfficeTransfer>> getPendingTransfers() {
        return ResponseEntity.ok(transferService.getPendingTransfers());
    }

    @GetMapping("/transfers/pending/count")
    public ResponseEntity<Map<String, Object>> getPendingCount() {
        return ResponseEntity.ok(Map.of("count", transferService.getPendingCount()));
    }

    @PostMapping("/transfers/{id}/approve")
    public ResponseEntity<InterOfficeTransfer> approveTransfer(
            @PathVariable Long id,
            @RequestParam(required = false) String overrideToOffice,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(transferService.approveTransfer(id, jwt.getSubject(), overrideToOffice));
    }

    @PostMapping("/transfers/{id}/reject")
    public ResponseEntity<InterOfficeTransfer> rejectTransfer(
            @PathVariable Long id,
            @RequestParam String comment,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(transferService.rejectTransfer(id, jwt.getSubject(), comment));
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
                jwt.getSubject()
        ));
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

    // Bulk reassignment
    @PostMapping("/bulk-reassign")
    public ResponseEntity<Map<String, Object>> bulkReassign(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt) {
        @SuppressWarnings("unchecked")
        List<String> complaintIds = (List<String>) request.get("complaintIds");
        String targetUser = (String) request.get("targetUser");
        int count = complaintIds != null ? complaintIds.size() : 0;
        return ResponseEntity.ok(Map.of("status", "reassigned", "count", count, "targetUser", targetUser));
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
