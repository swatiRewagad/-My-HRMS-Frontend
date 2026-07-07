package com.hrms.cms.controller;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealTimeline;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.security.AaRoleGuard;
import com.hrms.cms.service.AppealEligibilityService;
import com.hrms.cms.service.AppealWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/appeals")
@RequiredArgsConstructor
public class AppealController {

    private final AppealRepository appealRepository;
    private final AppealWorkflowService appealWorkflowService;
    private final AppealEligibilityService appealEligibilityService;

    private static final List<String> CLOSED_STATUSES = List.of("closed", "rejected", "order_passed");

    // ═══════════════════════════════════════════════════════════
    // Public endpoints (citizen-facing)
    // ═══════════════════════════════════════════════════════════

    /**
     * File a new appeal or representation.
     */
    @PostMapping("/file")
    public ResponseEntity<Map<String, Object>> fileAppeal(@RequestBody Map<String, String> request) {
        try {
            Map<String, Object> result = appealWorkflowService.fileAppeal(request);
            return buildResponse(true, "Appeal filed successfully", result);
        } catch (IllegalArgumentException e) {
            return buildResponse(false, e.getMessage(), null);
        }
    }

    /**
     * Check appeal eligibility for a complaint (public).
     */
    @GetMapping("/eligibility/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> checkEligibility(@PathVariable String complaintNumber) {
        Map<String, Object> result = appealEligibilityService.checkEligibility(complaintNumber);
        return buildResponse(true, "Eligibility check complete", result);
    }

    /**
     * Public status check for an appeal.
     */
    @GetMapping("/{appealNumber}/status")
    public ResponseEntity<Map<String, Object>> getAppealStatus(@PathVariable String appealNumber) {
        Optional<Appeal> opt = appealRepository.findByAppealNumber(appealNumber);
        if (opt.isEmpty()) {
            return buildResponse(false, "Appeal not found: " + appealNumber, null);
        }

        Appeal appeal = opt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appealNumber", appeal.getAppealNumber());
        data.put("classificationType", appeal.getClassificationType());
        data.put("status", appeal.getStatus());
        data.put("filedAt", appeal.getFiledAt() != null ? appeal.getFiledAt().toString() : "");
        data.put("hearingDate", appeal.getHearingDate() != null ? appeal.getHearingDate().toString() : null);
        data.put("orderOutcome", appeal.getOrderOutcome());
        data.put("orderDate", appeal.getOrderDate() != null ? appeal.getOrderDate().toString() : null);

        return buildResponse(true, "Appeal status retrieved", data);
    }

    // ═══════════════════════════════════════════════════════════
    // Staff endpoints
    // ═══════════════════════════════════════════════════════════

    /**
     * List appeals assigned to the current user/role (active tasks).
     */
    @GetMapping("/tasks")
    @AaRoleGuard(roles = {"AA_REGISTRAR", "AA_BENCH_OFFICER", "AA_AUTHORITY", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> getTasks(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String officer) {

        List<Appeal> tasks;
        if (officer != null && !officer.isBlank()) {
            tasks = appealRepository.findByAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(officer, CLOSED_STATUSES);
        } else if (role != null && !role.isBlank()) {
            tasks = appealRepository.findByAssignedRoleAndStatusNotInOrderByCreatedAtDesc(role, CLOSED_STATUSES);
        } else {
            tasks = appealRepository.findByStatusNotInOrderByCreatedAtDesc(CLOSED_STATUSES);
        }

        return buildResponse(true, "Appeal tasks retrieved", buildAppealList(tasks));
    }

    /**
     * Full detail view of a single appeal (staff only).
     */
    @GetMapping("/{appealNumber}")
    @AaRoleGuard(roles = {"AA_REGISTRAR", "AA_BENCH_OFFICER", "AA_AUTHORITY", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> getAppealDetail(@PathVariable String appealNumber) {
        Optional<Appeal> opt = appealRepository.findByAppealNumber(appealNumber);
        if (opt.isEmpty()) {
            return buildResponse(false, "Appeal not found: " + appealNumber, null);
        }

        Appeal appeal = opt.get();
        Map<String, Object> data = buildFullAppealDetail(appeal);

        // Include timeline
        List<AppealTimeline> timeline = appealWorkflowService.getTimeline(appealNumber);
        data.put("timeline", timeline.stream().map(t -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("action", t.getAction());
            entry.put("performedBy", t.getPerformedBy());
            entry.put("performedByRole", t.getPerformedByRole());
            entry.put("remarks", t.getRemarks());
            entry.put("fromStatus", t.getFromStatus());
            entry.put("toStatus", t.getToStatus());
            entry.put("performedAt", t.getPerformedAt() != null ? t.getPerformedAt().toString() : "");
            return entry;
        }).collect(Collectors.toList()));

        return buildResponse(true, "Appeal detail retrieved", data);
    }

    /**
     * Perform a workflow action on an appeal.
     */
    @PostMapping("/{appealNumber}/action")
    @AaRoleGuard(roles = {"AA_REGISTRAR", "AA_BENCH_OFFICER", "AA_AUTHORITY", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> performAction(
            @PathVariable String appealNumber,
            @RequestBody Map<String, String> request) {
        String action = request.getOrDefault("action", "");
        if (action.isBlank()) {
            return buildResponse(false, "Action is required", null);
        }

        try {
            Map<String, Object> result = appealWorkflowService.performAction(appealNumber, action, request);
            return buildResponse(true, "Action performed: " + action.toUpperCase(), result);
        } catch (IllegalArgumentException e) {
            return buildResponse(false, e.getMessage(), null);
        }
    }

    /**
     * Get available actions for an appeal based on user role.
     */
    @GetMapping("/{appealNumber}/available-actions")
    @AaRoleGuard(roles = {"AA_REGISTRAR", "AA_BENCH_OFFICER", "AA_AUTHORITY", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> getAvailableActions(
            @PathVariable String appealNumber,
            @RequestParam String userRole) {
        List<String> actions = appealWorkflowService.getAvailableActions(appealNumber, userRole);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appealNumber", appealNumber);
        data.put("userRole", userRole);
        data.put("availableActions", actions);
        return buildResponse(true, "Available actions", data);
    }

    /**
     * Dashboard statistics for the AA module.
     */
    @GetMapping("/stats")
    @AaRoleGuard(roles = {"AA_REGISTRAR", "AA_BENCH_OFFICER", "AA_AUTHORITY", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = appealWorkflowService.getStats();
        return buildResponse(true, "Appeal statistics", stats);
    }

    // ═══════════════════════════════════════════════════════════
    // Private helpers
    // ═══════════════════════════════════════════════════════════

    private List<Map<String, Object>> buildAppealList(List<Appeal> appeals) {
        return appeals.stream().map(a -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("appealId", a.getId());
            item.put("appealNumber", a.getAppealNumber());
            item.put("originalComplaintNumber", a.getOriginalComplaintNumber());
            item.put("classificationType", a.getClassificationType());
            item.put("appellantName", a.getAppellantName());
            item.put("status", a.getStatus() != null ? a.getStatus().toUpperCase() : "FILED");
            item.put("priority", a.getPriority() != null ? a.getPriority().toUpperCase() : "HIGH");
            item.put("assignedRole", a.getAssignedRole());
            item.put("assignedOfficer", a.getAssignedOfficer());
            item.put("workflowStage", a.getWorkflowStage());
            item.put("filedAt", a.getFiledAt() != null ? a.getFiledAt().toString() : "");
            item.put("hearingDate", a.getHearingDate() != null ? a.getHearingDate().toString() : null);
            return item;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> buildFullAppealDetail(Appeal a) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appealId", a.getId());
        data.put("appealNumber", a.getAppealNumber());
        data.put("originalComplaintNumber", a.getOriginalComplaintNumber());
        data.put("classificationType", a.getClassificationType());
        data.put("appealGround", a.getAppealGround());
        data.put("reliefSought", a.getReliefSought());
        data.put("appellantName", a.getAppellantName());
        data.put("appellantEmail", a.getAppellantEmail());
        data.put("appellantPhone", a.getAppellantPhone());
        data.put("status", a.getStatus());
        data.put("priority", a.getPriority());
        data.put("assignedRole", a.getAssignedRole());
        data.put("assignedOfficer", a.getAssignedOfficer());
        data.put("workflowStage", a.getWorkflowStage());
        data.put("filedAt", a.getFiledAt() != null ? a.getFiledAt().toString() : "");
        data.put("hearingDate", a.getHearingDate() != null ? a.getHearingDate().toString() : null);
        data.put("hearingVenue", a.getHearingVenue());
        data.put("orderDate", a.getOrderDate() != null ? a.getOrderDate().toString() : null);
        data.put("orderSummary", a.getOrderSummary());
        data.put("orderOutcome", a.getOrderOutcome());
        data.put("awardModifiedAmount", a.getAwardModifiedAmount() != null ? a.getAwardModifiedAmount().toPlainString() : null);
        data.put("closureCause", a.getClosureCause());
        data.put("closedAt", a.getClosedAt() != null ? a.getClosedAt().toString() : null);
        data.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : "");
        data.put("updatedAt", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : "");
        return data;
    }

    private ResponseEntity<Map<String, Object>> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
