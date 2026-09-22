package com.hrms.cms.controller;

import com.hrms.cms.dto.AaAssignmentRequest;
import com.hrms.cms.dto.AaAssignmentResult;
import com.hrms.cms.entity.AaAssignmentAudit;
import com.hrms.cms.security.AaRoleGuard;
import com.hrms.cms.service.AaAssignmentEngine;
import com.hrms.cms.service.AaOfficerPoolAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AA assignment and officer-pool administration.
 *
 * Every mutating endpoint is guarded to AA_ADMIN (plus ADMIN) because they all change who receives
 * citizen work. The guard is server-side via {@link AaRoleGuard}: the console hiding a button is not a
 * control, since these endpoints are reachable directly.
 *
 * Threshold and activation changes require a reason in the body rather than as a query parameter, so it
 * is not left in access logs and proxy histories.
 */
@RestController
@RequestMapping("/api/v1/aa/assignment")
@RequiredArgsConstructor
public class AaAssignmentController {

    private static final String[] ADMIN_ROLES = {"AA_ADMIN", "ADMIN"};

    private final AaAssignmentEngine assignmentEngine;
    private final AaOfficerPoolAdminService poolAdminService;

    /**
     * Places a record with an eligible officer.
     *
     * Available to the AA roles that create work, not only admins: S2A registers appeals and S2B
     * creates email/OCR drafts, and both must be able to trigger assignment. The engine still decides
     * WHO gets it — the caller cannot name a target through this endpoint.
     */
    @PostMapping("/assign")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN", "ADMIN"})
    public ResponseEntity<AaAssignmentResult> assign(@RequestBody AaAssignmentRequest request) {
        return ResponseEntity.ok(assignmentEngine.assign(request));
    }

    /** Story 9: AA Admin places a record by hand, bypassing the threshold. Reason is mandatory. */
    @PostMapping("/assign-manual")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<AaAssignmentResult> assignManually(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(assignmentEngine.assignManually(
                body.get("appealNumber"), body.get("targetUserId"), body.get("reason")));
    }

    /** Pool listing with live workload and the engine's own eligibility verdict per officer. */
    @GetMapping("/pool")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<List<Map<String, Object>>> pool(
            @RequestParam(required = false) String roleGroup) {
        return ResponseEntity.ok(poolAdminService.listPool(roleGroup));
    }

    /** Stories 2 and 6: per-officer threshold, applied from the next assignment onward. */
    @PutMapping("/pool/{userId}/threshold")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> updateThreshold(@PathVariable String userId,
                                                               @RequestBody Map<String, Object> body) {
        Object raw = body.get("threshold");
        if (raw == null) {
            throw new IllegalArgumentException("aa.pool.error_threshold_required");
        }
        int threshold;
        try {
            threshold = raw instanceof Number n
                    ? n.intValue()
                    : Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            // Otherwise a non-numeric body surfaces as a 500 rather than a client error.
            throw new IllegalArgumentException("aa.pool.error_threshold_required");
        }
        // Setting 0 means unlimited, so it must be asked for deliberately. See updateThreshold.
        boolean unlimited = Boolean.TRUE.equals(body.get("unlimited"));
        return ResponseEntity.ok(poolAdminService.updateThreshold(
                userId, threshold, asString(body.get("reason")), unlimited));
    }

    /** Story 8: which languages this officer can handle, as a CSV of ISO codes. */
    @PutMapping("/pool/{userId}/skills")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> updateSkills(@PathVariable String userId,
                                                            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(poolAdminService.updateSkills(
                userId, asString(body.get("skillLanguages")), asString(body.get("reason"))));
    }

    /**
     * Story 10: what the admin must see BEFORE deactivation is finalised.
     *
     * Separate read-only endpoint so the console can show the warning and let the admin cancel. The
     * deactivation itself then requires confirmed=true.
     */
    @PostMapping("/pool/deactivation-preview")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> previewDeactivation(
            @RequestBody Map<String, Object> body) {
        List<String> userIds = (List<String>) body.get("userIds");
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("aa.pool.error_no_officers_selected");
        }
        return ResponseEntity.ok(poolAdminService.previewDeactivation(userIds));
    }

    /** Story 5: bulk activate/deactivate, with per-officer isolation. */
    @PostMapping("/pool/bulk-activation")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> bulkActivation(@RequestBody Map<String, Object> body) {
        List<String> userIds = (List<String>) body.get("userIds");
        boolean active = Boolean.TRUE.equals(body.get("active"));
        boolean confirmed = Boolean.TRUE.equals(body.get("confirmed"));
        return ResponseEntity.ok(poolAdminService.bulkSetActive(
                userIds, active, confirmed, asString(body.get("reason"))));
    }

    /** Story 7: admin-triggered rebalance, for when auto-rebalance is off. */
    @PostMapping("/pool/rebalance")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> rebalance(@RequestBody Map<String, Object> body) {
        String roleGroup = asString(body.get("roleGroup"));
        if (roleGroup == null) {
            throw new IllegalArgumentException("aa.pool.error_role_group_required");
        }
        int moved = poolAdminService.rebalance(roleGroup, asString(body.get("reason")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleGroup", roleGroup);
        result.put("recordsMoved", moved);
        result.put("messageKey", "aa.pool.rebalance_complete");
        return ResponseEntity.ok(result);
    }

    /** Audit trail for one officer, so a threshold or activation change is answerable after the fact. */
    @GetMapping("/pool/{userId}/audit")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<List<AaAssignmentAudit>> audit(@PathVariable String userId) {
        return ResponseEntity.ok(poolAdminService.auditFor(userId));
    }

    /** Live chargeable workload for one officer. */
    @GetMapping("/workload/{userId}")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> workload(@PathVariable String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("currentWorkload", assignmentEngine.currentWorkload(userId));
        return ResponseEntity.ok(result);
    }

    private String asString(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }
}
