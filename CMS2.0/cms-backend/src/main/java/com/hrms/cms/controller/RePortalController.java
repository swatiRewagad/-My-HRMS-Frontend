package com.hrms.cms.controller;

import com.hrms.cms.entity.*;
import com.hrms.cms.security.ReRoleGuard;
import com.hrms.cms.service.ReNotificationService;
import com.hrms.cms.service.RePortalService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for the Regulated Entity (RE) portal.
 * Provides endpoints for banks/NBFCs to view and respond to complaints filed against them.
 */
@RestController
@RequestMapping("/api/v1/re-portal")
@RequiredArgsConstructor
@Slf4j
public class RePortalController {

    private final RePortalService rePortalService;
    private final ReNotificationService reNotificationService;

    // ═══════════════════════════════════════════════════════════════
    // Complaint listing
    // ═══════════════════════════════════════════════════════════════

    /**
     * List complaints forwarded to the logged-in entity.
     */
    @GetMapping("/complaints")
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> listComplaints(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            HttpServletRequest request) {

        String entityCode = extractEntityCode(request);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, sort));

        Page<Complaint> complaints = rePortalService.getComplaintsForEntity(entityCode, status, pageable);

        List<Map<String, Object>> items = complaints.getContent().stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("complaintNumber", c.getComplaintNumber());
            item.put("subject", c.getSubject());
            item.put("complainantName", c.getComplainantName());
            item.put("status", c.getStatus());
            item.put("priority", c.getPriority());
            item.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            item.put("filingType", c.getFilingType());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", items);
        response.put("page", complaints.getNumber());
        response.put("size", complaints.getSize());
        response.put("totalElements", complaints.getTotalElements());
        response.put("totalPages", complaints.getTotalPages());
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════════
    // Single complaint detail
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get single complaint detail.
     */
    @GetMapping("/complaints/{complaintNumber}")
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> getComplaintDetail(
            @PathVariable String complaintNumber,
            HttpServletRequest request) {

        String entityCode = extractEntityCode(request);

        try {
            Complaint c = rePortalService.getComplaintDetail(complaintNumber, entityCode);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("complaintNumber", c.getComplaintNumber());
            detail.put("subject", c.getSubject());
            detail.put("description", c.getDescription());
            detail.put("complainantName", c.getComplainantName());
            detail.put("status", c.getStatus());
            detail.put("priority", c.getPriority());
            detail.put("filingType", c.getFilingType());
            detail.put("reliefSought", c.getReliefSought());
            detail.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            detail.put("entityCode", c.getEntityCode());
            detail.put("withinResponseWindow", rePortalService.isWithinResponseWindow(complaintNumber));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", detail);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Response submission
    // ═══════════════════════════════════════════════════════════════

    /**
     * RE submits response to a complaint.
     */
    @PostMapping("/complaints/{complaintNumber}/respond")
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> respondToComplaint(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String entityCode = extractEntityCode(request);
        String responseText = (String) body.get("response");
        String respondedBy = (String) body.getOrDefault("respondedBy", "RE_USER");

        if (responseText == null || responseText.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Response text is required"));
        }

        try {
            // Validate entity ownership
            rePortalService.getComplaintDetail(complaintNumber, entityCode);

            ReResponseTracker tracker = rePortalService.respondToComplaint(complaintNumber, responseText, respondedBy);

            // Notify
            reNotificationService.notifyResponseReceived(complaintNumber, entityCode);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Response submitted successfully");
            response.put("respondedAt", tracker.getRespondedAt().toString());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Dashboard
    // ═══════════════════════════════════════════════════════════════

    /**
     * RE dashboard statistics.
     */
    @GetMapping("/dashboard")
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> getDashboard(HttpServletRequest request) {
        String entityCode = extractEntityCode(request);

        Map<String, Object> stats = rePortalService.getDashboardStats(entityCode);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", stats);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════════
    // Timeline
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get complaint timeline visible to the RE.
     */
    @GetMapping("/complaints/{complaintNumber}/timeline")
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> getTimeline(
            @PathVariable String complaintNumber,
            HttpServletRequest request) {

        String entityCode = extractEntityCode(request);

        try {
            List<ComplaintTimeline> timeline = rePortalService.getTimeline(complaintNumber, entityCode);

            List<Map<String, Object>> items = timeline.stream().map(t -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("action", t.getAction());
                item.put("performedBy", t.getPerformedBy());
                item.put("remarks", t.getRemarks());
                item.put("fromStatus", t.getFromStatus());
                item.put("toStatus", t.getToStatus());
                item.put("performedAt", t.getPerformedAt() != null ? t.getPerformedAt().toString() : null);
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", items);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Query / Clarification
    // ═══════════════════════════════════════════════════════════════

    /**
     * RE raises a query or seeks clarification/extension.
     */
    @PostMapping("/complaints/{complaintNumber}/query")
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> raiseQuery(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String entityCode = extractEntityCode(request);
        String queryText = (String) body.get("queryText");
        String queryType = (String) body.getOrDefault("queryType", "CLARIFICATION");

        if (queryText == null || queryText.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Query text is required"));
        }

        if (!"CLARIFICATION".equals(queryType) && !"EXTENSION_REQUEST".equals(queryType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "queryType must be CLARIFICATION or EXTENSION_REQUEST"));
        }

        try {
            // Validate entity ownership
            rePortalService.getComplaintDetail(complaintNumber, entityCode);

            rePortalService.raiseQuery(complaintNumber, queryText, queryType);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Query raised successfully");
            response.put("queryType", queryType);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Profile
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get RE entity profile.
     */
    @GetMapping("/profile")
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> getProfile(HttpServletRequest request) {
        String entityCode = extractEntityCode(request);

        try {
            RegulatedEntity entity = rePortalService.getEntityProfile(entityCode);

            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("id", entity.getId());
            profile.put("name", entity.getName());
            profile.put("entityType", entity.getEntityType());
            profile.put("department", entity.getDepartment());
            profile.put("city", entity.getCity());
            profile.put("state", entity.getState());
            profile.put("status", entity.getStatus());
            profile.put("nodalOfficerName", entity.getNodalOfficerName());
            profile.put("nodalOfficerEmail", entity.getNodalOfficerEmail());
            profile.put("nodalOfficerPhone", entity.getNodalOfficerPhone());
            profile.put("nodalOfficerDesignation", entity.getNodalOfficerDesignation());
            profile.put("pnoName", entity.getPnoName());
            profile.put("pnoEmail", entity.getPnoEmail());
            profile.put("pnoPhone", entity.getPnoPhone());
            profile.put("portalEnabled", entity.getPortalEnabled());
            profile.put("registrationDate", entity.getRegistrationDate() != null ? entity.getRegistrationDate().toString() : null);
            profile.put("lastLoginAt", entity.getLastLoginAt() != null ? entity.getLastLoginAt().toString() : null);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", profile);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Update nodal officer contact details.
     */
    @PutMapping("/profile/nodal-officer")
    @ReRoleGuard(roles = {"RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> updateNodalOfficer(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String entityCode = extractEntityCode(request);
        String name = (String) body.get("name");
        String email = (String) body.get("email");
        String phone = (String) body.get("phone");
        String designation = (String) body.get("designation");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Nodal officer name is required"));
        }

        try {
            RegulatedEntity entity = rePortalService.updateNodalOfficer(entityCode, name, email, phone, designation);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Nodal officer details updated");
            response.put("nodalOfficerName", entity.getNodalOfficerName());
            response.put("nodalOfficerEmail", entity.getNodalOfficerEmail());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Extracts entity code from the request JWT or headers.
     * Checks X-Entity-Code header first, then falls back to JWT claim.
     */
    private String extractEntityCode(HttpServletRequest request) {
        // Check direct header
        String entityCode = request.getHeader("X-Entity-Code");
        if (entityCode != null && !entityCode.isBlank()) {
            return entityCode.trim();
        }

        // Check X-User-Entity header (alternative)
        entityCode = request.getHeader("X-User-Entity");
        if (entityCode != null && !entityCode.isBlank()) {
            return entityCode.trim();
        }

        // Fallback: try extracting from JWT
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(payload, Map.class);
                    Object code = claims.get("entity_code");
                    if (code != null) return code.toString();
                }
            } catch (Exception e) {
                log.debug("Failed to extract entity_code from JWT: {}", e.getMessage());
            }
        }

        // Default fallback for dev mode
        log.warn("No entity code found in request headers or JWT - using default");
        return "UNKNOWN_ENTITY";
    }
}
