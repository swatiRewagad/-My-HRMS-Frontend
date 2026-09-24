package com.hrms.cms.controller;

import com.hrms.cms.entity.*;
import com.hrms.cms.security.ReRoleGuard;
import com.hrms.cms.service.FileStorageService;
import com.hrms.cms.service.FileUploadValidator;
import com.hrms.cms.service.ReNotificationService;
import com.hrms.cms.service.RePortalService;
import com.hrms.cms.service.UploadLimitsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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
    private final FileStorageService fileStorageService;
    private final FileUploadValidator fileUploadValidator;
    private final UploadLimitsService uploadLimits;

    /** True only under dev-local; the enforcing profile ignores X-Entity-Code entirely. */
    @Value("${cms.security.allow-dev-identity-headers:false}")
    private boolean allowDevIdentityHeaders;

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
            putActivityStatus(item, c);
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
            // Opening the detail is itself the "Opened" signal (UST846).
            Complaint c = rePortalService.openComplaintDetail(complaintNumber, entityCode, extractActor(request));

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
            putActivityStatus(detail, c);

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
     * RE submits response to a complaint, optionally with supporting documents.
     */
    @PostMapping(value = "/complaints/{complaintNumber}/respond", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> respondToComplaint(
            @PathVariable String complaintNumber,
            @RequestParam(value = "responseText", required = false) String responseText,
            @RequestParam(value = "documents", required = false) MultipartFile[] documents,
            @RequestParam(value = "respondedBy", required = false) String respondedBy,
            HttpServletRequest request) {

        return submitResponse(complaintNumber, responseText, respondedBy, documents, request);
    }

    /**
     * JSON variant retained for existing API/automation callers that send no documents.
     */
    @PostMapping(value = "/complaints/{complaintNumber}/respond", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> respondToComplaintJson(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String responseText = (String) body.get("response");
        if (responseText == null) {
            responseText = (String) body.get("responseText");
        }

        String respondedBy = (String) body.get("respondedBy");
        if (respondedBy == null) {
            respondedBy = (String) body.get("actor");
        }

        return submitResponse(complaintNumber, responseText, respondedBy, null, request);
    }

    private ResponseEntity<Map<String, Object>> submitResponse(
            String complaintNumber,
            String responseText,
            String respondedBy,
            MultipartFile[] documents,
            HttpServletRequest request) {

        String entityCode = extractEntityCode(request);
        String actor = (respondedBy == null || respondedBy.isBlank()) ? "RE_USER" : respondedBy;

        if (responseText == null || responseText.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Response text is required"));
        }

        List<MultipartFile> files = new ArrayList<>();
        if (documents != null) {
            for (MultipartFile file : documents) {
                if (file != null && !file.isEmpty()) {
                    files.add(file);
                }
            }
        }

        try {
            // Validate entity ownership
            Complaint complaint = rePortalService.getComplaintDetail(complaintNumber, entityCode);

            // Every file is validated before anything is written, so a rejected attachment
            // cannot leave earlier ones persisted against an unrecorded response.
            if (!files.isEmpty()) {
                int maxFiles = uploadLimits.maxFileCount();
                int existing = fileStorageService.getAttachments(complaint.getId()).size();
                if (existing + files.size() > maxFiles) {
                    return ResponseEntity.badRequest().body(Map.of("success", false,
                            "message", "Max files per complaint reached (" + maxFiles + ")"));
                }
                for (MultipartFile file : files) {
                    fileUploadValidator.validate(file);
                }
            }

            ReResponseTracker tracker = rePortalService.respondToComplaint(complaintNumber, responseText, actor);

            List<Map<String, Object>> attachments = new ArrayList<>();
            for (MultipartFile file : files) {
                ComplaintAttachment saved = fileStorageService.handleSingleUpload(
                        file, complaintNumber, complaint.getId());

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", saved.getId());
                item.put("originalName", saved.getOriginalName());
                item.put("contentType", saved.getContentType());
                item.put("fileSize", saved.getFileSize());
                attachments.add(item);
            }

            // Recorded only after the files are actually stored, so a failed upload cannot advance
            // the ladder. RESPONSE_SUBMITTED already outranks DOCUMENTS_UPLOADED, so the forward-only
            // rule discards this when documents accompany a submission — the history keeps both.
            if (!attachments.isEmpty()) {
                rePortalService.recordDocumentsUploaded(complaint, attachments.size(), actor);
            }

            // Notify
            reNotificationService.notifyResponseReceived(complaintNumber, entityCode);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Response submitted successfully");
            response.put("respondedAt", tracker.getRespondedAt().toString());
            response.put("attachments", attachments);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (FileUploadValidator.InvalidUploadException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to store RE response attachment for complaint {}: {}", complaintNumber, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to store attachment"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Draft response (UST846)
    // ═══════════════════════════════════════════════════════════════

    /**
     * RE saves a working draft. An empty draft records the entity as reviewing; a draft with text
     * records it as preparing a response.
     */
    @PostMapping("/complaints/{complaintNumber}/draft")
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> saveDraft(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String entityCode = extractEntityCode(request);
        String draftText = (String) body.get("draftText");

        try {
            ReResponseTracker tracker = rePortalService.saveDraft(
                    complaintNumber, entityCode, draftText, extractActor(request));
            Complaint c = rePortalService.getComplaintDetail(complaintNumber, entityCode);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Draft saved");
            response.put("draftSavedAt", tracker.getDraftSavedAt() != null
                    ? tracker.getDraftSavedAt().toString() : null);
            putActivityStatus(response, c);
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

    /**
     * Rejects any attempt to set the activity status directly (UST852).
     *
     * The ladder is derived from what the entity actually did. Exposing this endpoint purely to
     * refuse it is deliberate: an RE integration that guesses the URL gets an explicit,
     * documented 403 instead of a 404 that invites retrying a different path.
     */
    @PutMapping("/complaints/{complaintNumber}/activity-status")
    @ReRoleGuard(roles = {"RE_NODAL_OFFICER", "RE_PNO", "RE_ADMIN"})
    public ResponseEntity<Map<String, Object>> rejectManualActivityStatus(
            @PathVariable String complaintNumber) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "RE Activity Status is derived from your actions and cannot be set directly.",
                "messageKey", "re.activity.manual_set_forbidden"));
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
     * Adds the RE Activity Status to a response payload (UST846, UST847).
     *
     * The value is the enum name so both portals can key off one vocabulary, and the label is a
     * translation key rather than English text — the badge has to render identically on the RE and
     * RBI sides and both are localised.
     */
    private void putActivityStatus(Map<String, Object> target, Complaint complaint) {
        ReActivityStatus status = complaint.getReActivityStatus() == null
                ? ReActivityStatus.NOT_OPENED
                : complaint.getReActivityStatus();
        target.put("reActivityStatus", status.name());
        target.put("reActivityStatusKey", status.translationKey());
        target.put("reActivityChangedAt", complaint.getReActivityChangedAt() != null
                ? complaint.getReActivityChangedAt().toString() : null);
    }

    /** Best-effort actor for attribution. Falls back to a marker rather than inventing a username. */
    private String extractActor(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        return (userId == null || userId.isBlank()) ? "RE_USER" : userId.trim();
    }

    /**
     * Resolves the caller's regulated entity, JWT claim FIRST.
     *
     * This previously trusted the X-Entity-Code header ahead of the token and, failing everything,
     * returned the literal "UNKNOWN_ENTITY". Both were serious: every RE endpoint scopes its query by
     * this value, so any authenticated RE user could set one header and read another bank's
     * complaints, and the sentinel silently turned a failed resolution into a real-looking scope.
     *
     * The claim now wins. Headers are honoured only under dev-local (the E2E suites drive the API
     * with them), and an unresolvable entity throws instead of defaulting — a caller whose entity
     * cannot be established must receive no data rather than someone else's. This mirrors
     * RequestIdentityResolver, which already resolves RE identity claim-first for the same reason.
     */
    private String extractEntityCode(HttpServletRequest request) {
        String fromToken = entityCodeFromJwt(request);
        if (fromToken != null) {
            return fromToken;
        }

        if (allowDevIdentityHeaders) {
            String header = firstNonBlankHeader(request, "X-Entity-Code", "X-User-Entity");
            if (header != null) {
                return header;
            }
        }

        log.warn("Entity code could not be resolved for {} — rejecting request", request.getRequestURI());
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Your regulated entity could not be determined from your session. Please sign in again.");
    }

    private String entityCodeFromJwt(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String[] parts = authHeader.substring(7).split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(payload, Map.class);
            Object code = claims.get("entity_code");
            if (code != null && !code.toString().isBlank()) {
                return code.toString().trim();
            }
        } catch (Exception e) {
            log.debug("Failed to extract entity_code from JWT: {}", e.getMessage());
        }
        return null;
    }

    private String firstNonBlankHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
