package com.hrms.cms.controller;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealStatus;
import com.hrms.cms.entity.AppealTimeline;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.security.AaAccessDeniedException;
import com.hrms.cms.security.AaIdentityResolver;
import com.hrms.cms.security.AaRoleGuard;
import com.hrms.cms.service.AaIllegalTransitionException;
import com.hrms.cms.service.AaStageSlaService;
import com.hrms.cms.service.AaWorkflowTransition;
import com.hrms.cms.service.AppealClassificationService;
import com.hrms.cms.service.AppealEligibilityService;
import com.hrms.cms.service.AppealWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/appeals")
@RequiredArgsConstructor
public class AppealController {

    private final AppealRepository appealRepository;
    private final AppealWorkflowService appealWorkflowService;
    private final AppealEligibilityService appealEligibilityService;
    private final AaIdentityResolver aaIdentityResolver;
    private final AaStageSlaService stageSlaService;

    /**
     * Statuses in which an appeal is no longer live work.
     *
     * Sourced from AppealStatus, which is now the single authority. This was a hand-rolled copy of the
     * same three strings — one of three such copies — so AppealStatus was dead code and any change to
     * the vocabulary had to be made in three places or silently diverge.
     */
    private static final List<String> CLOSED_STATUSES = AppealStatus.TERMINAL_CODES;

    // ═══════════════════════════════════════════════════════════
    // Public endpoints (citizen-facing)
    // ═══════════════════════════════════════════════════════════

    /**
     * File a new appeal or representation.
     * Accepts multipart form data: text fields via @RequestParam, files via @RequestPart.
     */
    @PostMapping("/file")
    public ResponseEntity<Map<String, Object>> fileAppeal(
            @RequestParam String complaintNumber,
            @RequestParam String ground,
            @RequestParam String details,
            @RequestParam(required = false) String reliefSought,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String reasonForDelay,
            @RequestPart(required = false) MultipartFile[] attachments) {

        // Validate required fields
        if (complaintNumber == null || complaintNumber.isBlank()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "complaintNumber is required");
        }
        if (ground == null || ground.isBlank()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Appeal ground is required");
        }
        if (details == null || details.isBlank()) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Appeal details are required");
        }

        // Validate appealGround length (maps to appealGround on Appeal entity)
        String appealGround = details;
        if (appealGround.length() > 5000) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Appeal details must not exceed 5000 characters");
        }

        // Determine if this is a delayed filing based on eligibility check
        Map<String, Object> eligibility = appealEligibilityService.checkEligibility(complaintNumber);
        boolean delayedFiling = Boolean.TRUE.equals(eligibility.get("delayedFiling"));

        // Validate reasonForDelay when delayed filing
        if (delayedFiling) {
            if (reasonForDelay == null || reasonForDelay.isBlank()) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST,
                        "Reason for delay is required when filing after the standard window");
            }
            if (reasonForDelay.length() > 500) {
                return buildErrorResponse(HttpStatus.BAD_REQUEST,
                        "Reason for delay must not exceed 500 characters");
            }
        }

        // Check eligibility
        if (!Boolean.TRUE.equals(eligibility.get("eligible"))) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST,
                    "Appeal not eligible: " + eligibility.get("reason"));
        }

        // Check for duplicate active appeal
        List<Appeal> existingAppeals = appealRepository.findByOriginalComplaintNumber(complaintNumber);
        boolean hasDuplicate = existingAppeals.stream()
                .anyMatch(a -> !CLOSED_STATUSES.contains(a.getStatus()));
        if (hasDuplicate) {
            return buildErrorResponse(HttpStatus.CONFLICT,
                    "An active appeal already exists for complaint: " + complaintNumber);
        }

        try {
            // Build the request map for the workflow service
            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", complaintNumber);
            request.put("classificationType", classification != null && !classification.isBlank()
                    ? classification : "APPEAL");
            request.put("appealGround", ground + "\n\n" + details);
            request.put("reliefSought", reliefSought != null ? reliefSought : "");
            // Appellant details will be derived from complaint in the workflow service
            request.put("appellantName", "Citizen"); // Placeholder — workflow service can override from complaint
            if (reasonForDelay != null && !reasonForDelay.isBlank()) {
                request.put("reasonForDelay", reasonForDelay);
            }

            Map<String, Object> result = appealWorkflowService.fileAppeal(request, attachments);
            return buildResponse(HttpStatus.CREATED, true, "Appeal filed successfully", result);
        } catch (AppealClassificationService.UnmappedClauseException e) {
            // Rethrown so GlobalExceptionHandler can answer 503 with a translation key and alert AA
            // Admin. The generic catch below would otherwise flatten it to an opaque 500.
            throw e;
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred while filing the appeal");
        }
    }

    /**
     * Check appeal eligibility for a complaint (public).
     * Frontend calls: GET /api/v1/appeals/check-eligibility?complaintNumber=...
     */
    @GetMapping("/check-eligibility")
    public ResponseEntity<Map<String, Object>> checkEligibilityByParam(
            @RequestParam String complaintNumber) {
        Map<String, Object> result = appealEligibilityService.checkEligibility(complaintNumber);
        return buildResponse(HttpStatus.OK, true, "Eligibility check complete", result);
    }

    /**
     * Legacy eligibility endpoint (backward compatible).
     */
    @GetMapping("/eligibility/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> checkEligibility(@PathVariable String complaintNumber) {
        Map<String, Object> result = appealEligibilityService.checkEligibility(complaintNumber);
        return buildResponse(HttpStatus.OK, true, "Eligibility check complete", result);
    }

    /**
     * Public status check for an appeal.
     */
    @GetMapping("/{appealNumber}/status")
    public ResponseEntity<Map<String, Object>> getAppealStatus(@PathVariable String appealNumber) {
        Optional<Appeal> opt = appealRepository.findByAppealNumber(appealNumber);
        if (opt.isEmpty()) {
            return buildResponse(HttpStatus.OK, false, "Appeal not found: " + appealNumber, null);
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

        return buildResponse(HttpStatus.OK, true, "Appeal status retrieved", data);
    }

    // ═══════════════════════════════════════════════════════════
    // Staff endpoints
    // ═══════════════════════════════════════════════════════════

    /**
     * Filterable appeal list — the AA home views.
     *
     * This mapping did not exist. The dashboard called GET /api/v1/appeals, got no handler, and its
     * error branch swallowed the failure, so the grid silently rendered empty against a live backend.
     *
     * Filters compose, which is what the five views need:
     *   classification=APPEAL|REPRESENTATION, openOnly=true, assignedOfficer=me, createdBy=me.
     * `me` resolves to the caller's own id server-side, so one user cannot request another's queue.
     */
    @GetMapping
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> listAppeals(
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedOfficer,
            @RequestParam(required = false) String assignedRole,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "false") boolean openOnly) {

        String caller = aaIdentityResolver.resolveActor();
        List<Appeal> appeals = appealRepository.findForView(
                normalize(classification),
                resolveMe(assignedOfficer, caller),
                normalize(assignedRole),
                resolveMe(createdBy, caller),
                normalize(status),
                openOnly,
                CLOSED_STATUSES);

        return buildResponse(HttpStatus.OK, true, "Appeals retrieved", buildAppealList(appeals));
    }

    /**
     * Resolves the literal "me" to the authenticated caller.
     *
     * Lets the client express "assigned to me" without naming a user, so a caller cannot substitute
     * someone else's id and read their queue.
     */
    private String resolveMe(String value, String caller) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "me".equalsIgnoreCase(value.trim()) ? caller : value.trim();
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * List appeals assigned to the current user/role (active tasks).
     */
    @GetMapping("/tasks")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
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

        return buildResponse(HttpStatus.OK, true, "Appeal tasks retrieved", buildAppealList(tasks));
    }

    /**
     * Full detail view of a single appeal (staff only).
     */
    @GetMapping("/{appealNumber}")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> getAppealDetail(@PathVariable String appealNumber) {
        Optional<Appeal> opt = appealRepository.findByAppealNumber(appealNumber);
        if (opt.isEmpty()) {
            return buildResponse(HttpStatus.OK, false, "Appeal not found: " + appealNumber, null);
        }

        Appeal appeal = opt.get();
        Map<String, Object> data = buildFullAppealDetail(appeal);
        data.put("timeline", buildTimeline(appealNumber));

        return buildResponse(HttpStatus.OK, true, "Appeal detail retrieved", data);
    }

    private List<Map<String, Object>> buildTimeline(String appealNumber) {
        List<AppealTimeline> timeline = appealWorkflowService.getTimeline(appealNumber);
        return timeline.stream().map(t -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("action", t.getAction());
            entry.put("performedBy", t.getPerformedBy());
            entry.put("performedByRole", t.getPerformedByRole());
            entry.put("remarks", t.getRemarks());
            entry.put("fromStatus", t.getFromStatus());
            entry.put("toStatus", t.getToStatus());
            // Field-level audit, populated for a classification override.
            entry.put("fieldName", t.getFieldName());
            entry.put("oldValue", t.getOldValue());
            entry.put("newValue", t.getNewValue());
            entry.put("performedAt", t.getPerformedAt() != null ? t.getPerformedAt().toString() : "");
            return entry;
        }).collect(Collectors.toList());
    }

    /**
     * Timeline for one appeal, including field-level override entries.
     *
     * The detail component already called this URL; there was no handler, so the History panel was
     * permanently empty. The timeline is also embedded in the detail payload, but a standalone route
     * is what the 3-dot History action needs.
     */
    @GetMapping("/{appealNumber}/timeline")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> getAppealTimeline(@PathVariable String appealNumber) {
        if (appealRepository.findByAppealNumber(appealNumber).isEmpty()) {
            return buildResponse(HttpStatus.OK, false, "Appeal not found: " + appealNumber, null);
        }
        List<Map<String, Object>> timeline = buildTimeline(appealNumber);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appealNumber", appealNumber);
        data.put("timeline", timeline);
        return buildResponse(HttpStatus.OK, true, "Appeal timeline retrieved", data);
    }

    /**
     * Perform a workflow action on an appeal.
     */
    @PostMapping("/{appealNumber}/action")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> performAction(
            @PathVariable String appealNumber,
            @RequestBody Map<String, String> request) {
        String action = request.getOrDefault("action", "");
        if (action.isBlank()) {
            return buildResponse(HttpStatus.OK, false, "Action is required", null);
        }

        try {
            Map<String, Object> result = appealWorkflowService.performAction(appealNumber, action, request);
            return buildResponse(HttpStatus.OK, true, "Action performed: " + action.toUpperCase(), result);
        } catch (AaAccessDeniedException e) {
            // 403, not 200-with-success=false: a denial has to be distinguishable from a bad field.
            return buildErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (AaIllegalTransitionException e) {
            // 409 CONFLICT: the caller is authorised and the request is well-formed, but the appeal has
            // moved on. A 400 would point at the payload and a 403 at permissions — both would send an
            // operator looking in the wrong place. The available actions come back so the client can
            // correct itself in one round trip.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("messageKey", e.getMessageKey());
            body.put("message", e.getMessage());
            body.put("currentStatus", e.getCurrentStatus());
            body.put("attemptedAction", e.getAction());
            body.put("availableActions", e.getAvailableActions());
            body.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        } catch (DateTimeParseException e) {
            // Otherwise this escapes as a 500. It is a malformed field, so it must read as one: the
            // hearing date is submitted as a string and a date-only value like '2026-09-30' fails
            // ISO_LOCAL_DATE_TIME parsing.
            return buildResponse(HttpStatus.OK, false,
                    "hearingDate must be an ISO date-time, e.g. 2026-09-30T11:00", null);
        } catch (IllegalArgumentException e) {
            return buildResponse(HttpStatus.OK, false, e.getMessage(), null);
        }
    }

    /**
     * Actions available to the CURRENT caller, resolved from their token.
     *
     * userRole used to be a request parameter, so the client could ask for another role's action set
     * and render buttons that performAction would then reject.
     */
    @GetMapping("/{appealNumber}/available-actions")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> getAvailableActions(@PathVariable String appealNumber) {
        List<String> actions = appealWorkflowService.getAvailableActions(appealNumber);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appealNumber", appealNumber);
        data.put("userRole", aaIdentityResolver.resolveAaRole());
        data.put("availableActions", actions);
        return buildResponse(HttpStatus.OK, true, "Available actions", data);
    }

    /**
     * Manually re-classify an appeal, with a mandatory reason and a field-level audit record.
     *
     * Separate from /action deliberately: performAction rejects any classification change so the
     * clause-derived value stays immutable in the ordinary flow. The Scheme still needs an override
     * for edge cases, so it gets its own explicitly-audited route rather than a hole in that guard.
     */
    @PutMapping("/{appealNumber}/classification")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> overrideClassification(
            @PathVariable String appealNumber,
            @RequestBody Map<String, String> request) {
        try {
            Map<String, Object> result = appealWorkflowService.overrideClassification(
                    appealNumber,
                    request.get("classificationType"),
                    request.get("reason"));
            return buildResponse(HttpStatus.OK, true, "Classification overridden", result);
        } catch (AaAccessDeniedException e) {
            return buildErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Dashboard statistics for the AA module.
     */
    @GetMapping("/stats")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = appealWorkflowService.getStats();
        return buildResponse(HttpStatus.OK, true, "Appeal statistics", stats);
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
            // Both keys: the frontend binds `classification`, existing API consumers read
            // `classificationType`. The mismatch is why both badges rendered blank.
            item.put("classification", a.getClassificationType());
            item.put("classificationOverridden", a.isClassificationOverridden());
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
        // Both keys: the frontend binds `classification`, existing consumers read
        // `classificationType`. The mismatch is why both badges rendered blank.
        data.put("classification", a.getClassificationType());
        data.put("classificationOverridden", a.isClassificationOverridden());
        data.put("classificationOverrideReason", a.getClassificationOverrideReason());
        data.put("classificationOverriddenBy", a.getClassificationOverriddenBy());
        data.put("classificationOverriddenAt",
                a.getClassificationOverriddenAt() != null ? a.getClassificationOverriddenAt().toString() : null);
        data.put("createdBy", a.getCreatedBy());
        data.put("createdByRole", a.getCreatedByRole());
        data.put("entityCode", a.getEntityCode());
        data.put("modeOfReceipt", a.getModeOfReceipt());
        data.put("appealFiledBy", a.getAppealFiledBy());
        data.put("closureClause", a.getClosureClause());
        data.put("appealGround", a.getAppealGround());
        data.put("reliefSought", a.getReliefSought());
        data.put("reasonForDelay", a.getReasonForDelay());
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
        // Per-stage SLA, computed server-side in working days against the HOLIDAYS master. Deliberately
        // NOT left to the browser: a client-side copy of working-day arithmetic would disagree with the
        // server about public holidays, and the deadline shown to an officer must be the one the system
        // is actually measuring them against.
        data.put("sla", stageSlaService.describe(a));

        // What this caller may do next, from the one transition table the server enforces. Included here
        // so a detail view never has to derive actions itself — that drift is why officers saw screens
        // with no available actions.
        data.put("availableActions", AaWorkflowTransition.availableFor(
                aaIdentityResolver.resolveAaRole(), a.getStatus()));

        return data;
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, boolean success,
                                                               String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        return buildResponse(status, false, message, null);
    }
}
