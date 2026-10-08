package com.hrms.cms.controller;

import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.entity.RbioStatusMaster;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RbioStatusMasterRepository;
import com.hrms.cms.repository.SimulatedEmailRepository;
import com.hrms.cms.service.CepcAuditService;
import com.hrms.cms.service.CitizenSessionService;
import com.hrms.cms.service.CitizenStageMapper;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.AnomalyDetectionService;
import com.hrms.cms.service.FileStorageService;
import com.hrms.cms.service.NotificationConfigService;
import com.hrms.cms.service.NotificationService;
import com.hrms.cms.service.PiiMaskingService;
import com.hrms.cms.service.PiiRevealService;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.triage.IntakeTriageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@Slf4j
public class ComplaintApiV1Controller {

    private final ComplaintService complaintService;
    private final BankRepository bankRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final SimulatedEmailRepository simulatedEmailRepository;
    private final ComplaintRepository complaintRepository;
    // RBIO_STATUS_MASTER backs the milestone ladder; see milestoneLadder().
    private final RbioStatusMasterRepository rbioStatusMasterRepository;
    private final IntakeTriageService triageService;
    // NOTE: these two were silently dropped by the automatic merge (ours deleted them relative to the
    // merge base while theirs left them untouched, so a non-conflicting 3-way merge took ours'
    // deletion) even though resolveCitizenMobile() and the withdrawal/track audit calls kept from the
    // incoming side still require them. Restored here so that kept code compiles.
    private final CitizenSessionService citizenSessionService;
    private final CepcAuditService auditService;
    private final Validator validator;
    private final com.hrms.cms.service.ComplaintRoutingService complaintRoutingService;
    private final com.hrms.cms.service.KeycloakUserService keycloakUserService;
    private final com.hrms.cms.repository.OfficerAvailabilityRepository officerAvailabilityRepository;
    private final com.hrms.cms.repository.OfficeCodeMasterRepository officeCodeMasterRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.hrms.cms.repository.ClosureClauseMasterRepository closureClauseMasterRepository;
    private final com.hrms.cms.service.ComplaintLetterPdfService complaintLetterPdfService;
    private final com.hrms.cms.service.AppealClassificationService appealClassificationService;
    private final RequestIdentityResolver requestIdentityResolver;
    private final PiiMaskingService piiMaskingService;
    private final PiiRevealService piiRevealService;
    private final AnomalyDetectionService anomalyDetectionService;

    /**
     * Stores the documents a citizen submits WITH a withdrawal (UST107 / FR-G-036 scenario 4).
     *
     * <p>The withdrawal endpoint accepted a JSON body only, so the files the withdrawal form happily
     * collected had nowhere to go: they were held in a browser field, the citizen saw a confirmation,
     * and the documents were discarded on navigation. The same service the officer upload path uses
     * is reused here so provenance, limits and byte-signature checks are identical — the only
     * difference is the recorded source (COMPLAINANT, not OFFICER).
     */
    private final FileStorageService fileStorageService;

    /** Tells the officers holding a case that the complainant has withdrawn it (UST108 / FR-G-037). */
    private final NotificationService notificationService;

    /** Supplies the configured withdrawal recipient list rather than a hardcoded one. */
    private final NotificationConfigService notificationConfigService;

    private static final String CITIZEN_TOKEN_HEADER = "X-Citizen-Token";

    /** The pseudo-status the citizen tracking filter sends for "everything still outstanding". */
    private static final String STATUS_FILTER_OPEN = "OPEN";

    /**
     * The statuses that END a citizen's wait, and so are NOT Open.
     *
     * Business ruling: a complaint is Open until it is resolved. Deliberately separate from
     * cms.duplicate-check.terminal-statuses — that list governs whether a citizen may RE-FILE, which is
     * a different question: a resolved complaint is no longer Open but re-filing on it is still barred.
     */
    @org.springframework.beans.factory.annotation.Value(
            "${cms.citizen-tracking.settled-statuses:resolved,closed,rejected,withdrawn}")
    private List<String> settledStatuses;

    /** Days from the RE COMPLAINT date, when the RE never replied. Same property the wizard is served. */
    @org.springframework.beans.factory.annotation.Value("${cms.eligibility.grievance-filing-window-days:310}")
    private int grievanceFilingWindowDays;

    /** Days from the RE REPLY date, when the RE did reply. Same property the wizard is served. */
    @org.springframework.beans.factory.annotation.Value("${cms.mre.filing-deadline-days:90}")
    private int postReplyFilingWindowDays;

    /** Resolves the citizen session token from header or bearer, then maps it to the owning mobile. */
    private String resolveCitizenMobile(HttpServletRequest request) {
        String token = request.getHeader(CITIZEN_TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }
        }
        return citizenSessionService.resolveMobile(token);
    }

    private Map<String, Object> errorBody(String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        body.put("message", message);
        body.put("data", null);
        body.put("correlationId", UUID.randomUUID().toString());
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> registerComplaint(@RequestBody Map<String, Object> request) {
        FileComplaintRequest req = new FileComplaintRequest();
        req.setComplainantName((String) request.getOrDefault("complainantName", ""));
        req.setComplainantEmail((String) request.getOrDefault("complainantEmail", ""));
        req.setComplainantPhone((String) request.getOrDefault("complainantPhone", ""));
        req.setComplainantAddress((String) request.get("complainantAddress"));
        req.setComplainantState((String) request.get("complainantState"));
        req.setComplainantDistrict((String) request.get("complainantDistrict"));
        req.setComplainantPincode(trimToNull(request.get("complainantPincode")));
        req.setSubject((String) request.getOrDefault("subject", ""));
        req.setDescription((String) request.getOrDefault("description", ""));
        req.setPriority((String) request.getOrDefault("priority", "medium"));
        req.setFilingType((String) request.getOrDefault("filingType", "ONLINE"));

        if (request.get("regulatedEntityId") != null) {
            req.setRegulatedEntityId(Long.valueOf(request.get("regulatedEntityId").toString()));
        }
        if (request.get("entityName") != null) {
            req.setEntityName(request.get("entityName").toString());
        }
        if (request.get("entityType") != null) {
            req.setEntityType(request.get("entityType").toString());
        }
        if (request.get("priorReComplaint") != null) {
            req.setPriorReComplaint(Boolean.valueOf(request.get("priorReComplaint").toString()));
        }
        if (request.get("reComplaintDate") != null) {
            req.setReComplaintDate(java.time.LocalDate.parse(request.get("reComplaintDate").toString()));
        }
        if (request.get("reComplaintReference") != null) {
            req.setReComplaintReference(request.get("reComplaintReference").toString());
        }
        if (request.get("reRepliedAndDissatisfied") != null) {
            req.setReRepliedAndDissatisfied(Boolean.valueOf(request.get("reRepliedAndDissatisfied").toString()));
        }
        if (request.get("amountInvolved") != null) {
            req.setAmountInvolved(new java.math.BigDecimal(request.get("amountInvolved").toString()));
        }

        // Fields the wizard collects that were previously dropped on the floor here, leaving the
        // review screen blank even though the citizen had filled them in.
        req.setReliefSought(trimToNull(request.get("reliefSought")));
        req.setBankBranch(trimToNull(request.get("bankBranch")));
        req.setAccountNumber(trimToNull(request.get("accountNumber")));
        req.setBankComplaintReference(trimToNull(request.get("bankComplaintReference")));
        req.setRepName(trimToNull(request.get("repName")));
        req.setRepEmail(trimToNull(request.get("repEmail")));
        req.setRepPhone(trimToNull(request.get("repPhone")));
        req.setEntityState(trimToNull(request.get("entityState")));
        req.setEntityDistrict(trimToNull(request.get("entityDistrict")));
        req.setEntityBranchName(trimToNull(request.get("entityBranchName")));
        req.setBankComplaintDate(trimToNull(request.get("transactionDate")));
        if (request.get("eligibilityAnswers") instanceof Map<?, ?> ea) {
            req.setEligibilityAnswers((Map<String, Object>) ea);
        }
        if (request.get("wizardFormData") instanceof Map<?, ?> wf) {
            req.setWizardFormData((Map<String, Object>) wf);
        }
        req.setNonMaintainableClauseCode(trimToNull(request.get("nonMaintainableClauseCode")));
        req.setNonMaintainableStatusCode(trimToNull(request.get("nonMaintainableStatusCode")));
        req.setDuplicateOfComplaintNumber(trimToNull(request.get("duplicateOfComplaintNumber")));
        if (request.get("reReplyDate") != null && !request.get("reReplyDate").toString().isBlank()) {
            req.setReReplyDate(java.time.LocalDate.parse(request.get("reReplyDate").toString()));
        }
        if (request.get("declarationAccepted") != null) {
            req.setDeclarationAccepted(Boolean.valueOf(request.get("declarationAccepted").toString()));
        }

        // Resolve category name to ID
        if (request.get("category") != null) {
            String categoryName = request.get("category").toString();
            req.setCategoryName(categoryName);
            categoryRepository.findFirstByNameIgnoreCase(categoryName)
                    .ifPresent(cat -> req.setCategoryId(cat.getId()));
        }
        if (req.getCategoryId() == null && request.get("categoryId") != null) {
            req.setCategoryId(Long.valueOf(request.get("categoryId").toString()));
        }

        Set<ConstraintViolation<FileComplaintRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Validation failed: " + errors));
        }

        String timeBar = filingWindowRefusal(req);
        if (timeBar != null) {
            return ResponseEntity.badRequest().body(
                    errorBody("FILING_PERIOD_EXPIRED", timeBar));
        }

        Complaint c = complaintService.fileComplaint(req);

        try {
            triageService.triageOnRegistration(c);
        } catch (Exception e) {
            // DELIBERATELY SWALLOWED, and logged rather than silent. A triage failure must not lose a
            // citizen's complaint: the complaint is already persisted by this point, and triage only
            // assigns priority/category hints that staff can set by hand. Rethrowing would fail the
            // registration and discard a filing the citizen believes succeeded.
            log.warn("Triage failed for {} after registration; complaint stands untriaged: {}",
                    c.getComplaintNumber(), e.toString());
        }

        boolean nonMaintainable = c.getCaseId() != null;

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("complaintId", nonMaintainable ? c.getCaseId() : c.getComplaintNumber());
        ack.put("caseId", c.getCaseId());
        ack.put("complaintNumber", c.getComplaintNumber());
        ack.put("status", nonMaintainable ? "PORTAL_REJECTION" : "REGISTERED");
        ack.put("registeredAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : LocalDateTime.now().toString());
        ack.put("slaDueDate", c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : LocalDateTime.now().plusDays(30).toString());
        ack.put("acknowledgementMessage", nonMaintainable
                ? "Your complaint has been determined Non-Maintainable. Use the Case ID to reference this closure."
                : "Your complaint has been registered successfully. Use the reference number to track status.");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Complaint registered successfully");
        response.put("data", ack);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * FR-G-013: issues a real Case ID for a complaint the eligibility wizard determined
     * Non-Maintainable, before the citizen has reached (or filled in) the full filing form.
     *
     * <p>Only the fields the eligibility stage actually knows are required; subject/description are
     * not available yet, so this builds a minimal {@link FileComplaintRequest} rather than reusing
     * {@link #registerComplaint}'s full field mapping.
     */
    @PostMapping("/non-maintainable")
    public ResponseEntity<Map<String, Object>> registerNonMaintainable(@RequestBody Map<String, Object> request) {
        String clauseCode = trimToNull(request.get("clauseCode"));
        if (clauseCode == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "clauseCode is required"));
        }

        FileComplaintRequest req = new FileComplaintRequest();
        req.setComplainantName(Objects.toString(request.getOrDefault("complainantName", "Not provided")));
        req.setComplainantEmail(trimToNull(request.get("complainantEmail")));
        req.setComplainantPhone(trimToNull(request.get("complainantPhone")));
        req.setComplainantState(trimToNull(request.get("complainantState")));
        req.setComplainantDistrict(trimToNull(request.get("complainantDistrict")));
        req.setEntityName(trimToNull(request.get("entityName")));
        req.setSubject("Non-Maintainable at eligibility screening — clause " + clauseCode);
        req.setDescription("Complaint determined Non-Maintainable at the eligibility screening stage.");
        req.setNonMaintainableClauseCode(clauseCode);
        req.setNonMaintainableStatusCode(trimToNull(request.get("statusCode")));

        Set<ConstraintViolation<FileComplaintRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Validation failed: " + errors));
        }

        Complaint c = complaintService.fileComplaint(req);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caseId", c.getCaseId());
        data.put("status", "PORTAL_REJECTION");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Case ID issued");
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * The statutory filing window, enforced on the SERVER because the browser check is bypassable.
     *
     * Business ruling, and the two limbs are genuinely different rules:
     *
     *   RE never replied  → the window runs from the RE complaint date and is
     *                       cms.eligibility.grievance-filing-window-days (310). Past it, the complaint is
     *                       REFUSED; the citizen's recourse is the system's own auto-closure of the
     *                       unanswered RE complaint, not a fresh filing here.
     *   RE did reply      → the window runs from the REPLY date and is cms.mre.filing-deadline-days (90).
     *                       The RE complaint date is then irrelevant: a reply on day 300 still leaves a
     *                       full 90 days, which is why this cannot be expressed as one window.
     *
     * Both bounds are INCLUSIVE — filing on the last day is timely — matching the browser-side check so
     * the two layers cannot disagree about the boundary.
     *
     * Returns the citizen-facing refusal, or null when the filing is timely.
     */
    private String filingWindowRefusal(FileComplaintRequest req) {
        if (!Boolean.TRUE.equals(req.getPriorReComplaint())) {
            // No prior RE complaint means no window has started running.
            return null;
        }

        if (Boolean.TRUE.equals(req.getReRepliedAndDissatisfied()) && req.getReReplyDate() != null) {
            long since = java.time.temporal.ChronoUnit.DAYS.between(req.getReReplyDate(), java.time.LocalDate.now());
            if (since > postReplyFilingWindowDays) {
                return "Complaint filing period has expired. A complaint must be filed within "
                        + postReplyFilingWindowDays + " days of the Regulated Entity's reply, and "
                        + since + " days have elapsed.";
            }
            return null;
        }

        if (req.getReComplaintDate() == null) {
            return null;
        }
        long since = java.time.temporal.ChronoUnit.DAYS.between(req.getReComplaintDate(), java.time.LocalDate.now());
        if (since > grievanceFilingWindowDays) {
            return "Complaint filing period has expired. A complaint must be filed within "
                    + grievanceFilingWindowDays + " days of your complaint to the Regulated Entity, and "
                    + since + " days have elapsed.";
        }
        return null;
    }

    /**
     * Duplicate pre-check the wizard runs before submitting (UST87 / FR-G-020).
     *
     * <p>FR-G-020 defines a duplicate as the full combination
     * (mobile OR email) + complainant name + entity name + date of disputed transaction + category,
     * against a complaint that is still live. Terminal statuses are excluded so a citizen may
     * re-file once a matter is closed.
     *
     * <p>Name, entity, date and category are passed through as the citizen typed them and matched
     * in {@link ComplaintService} against the columns public filing populates. Resolving them to
     * BANKS/CATEGORY ids here is what broke the control: the portal's entity dropdown is fed from
     * REGULATED_ENTITIES and its categories are codes, so neither id ever resolved, and a null id
     * meant "ignore this field" — reducing the check to "has this phone filed anything recently".
     */
    @PostMapping("/check-duplicate")
    public ResponseEntity<Map<String, Object>> checkDuplicate(@RequestBody Map<String, Object> request) {
        String phone = trimToNull(request.get("phone"));
        String email = trimToNull(request.get("email"));

        if (phone == null && email == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "MISSING_IDENTIFIER",
                    "message", "A phone or an email is required"));
        }

        String complainantName = trimToNull(request.get("complainantName"));
        String entityName = trimToNull(request.get("entityName"));
        String category = trimToNull(request.get("category"));

        java.time.LocalDate disputeDate = null;
        String rawDisputeDate = trimToNull(request.get("disputeDate"));
        if (rawDisputeDate != null) {
            try {
                disputeDate = java.time.LocalDate.parse(rawDisputeDate);
            } catch (java.time.format.DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "INVALID_DISPUTE_DATE",
                        "message", "disputeDate must be an ISO date (yyyy-MM-dd)"));
            }
        }

        List<Complaint> matches = complaintService.findPotentialDuplicates(
                phone, email, complainantName, entityName, category, disputeDate);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("timestamp", LocalDateTime.now().toString());

        if (matches.isEmpty()) {
            response.put("duplicate", false);
            return ResponseEntity.ok(response);
        }

        Complaint match = matches.get(0);
        // Phone is the stronger signal, so report it whenever it is what actually matched.
        boolean phoneMatched = phone != null && phone.equals(match.getComplainantPhone());

        response.put("duplicate", true);
        response.put("matchedOn", phoneMatched ? "phone" : "email");
        response.put("complaintNumber", match.getComplaintNumber());
        response.put("status", match.getStatus());
        response.put("filedAt", match.getCreatedAt() != null ? match.getCreatedAt().toString() : null);
        return ResponseEntity.ok(response);
    }

    private String trimToNull(Object value) {
        if (value == null) return null;
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getComplaintsByPhone(@RequestParam String phone) {
        List<Complaint> complaints = complaintService.getByComplainantPhone(phone);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        // Resolved once for the whole page rather than per row — the clause master is small and
        // static, and a lookup inside the map() would re-query for every complaint.
        Map<String, String> clauseLabels = closureClauseMasterRepository.findAll().stream()
                .collect(Collectors.toMap(
                        m -> m.getClauseCode().toUpperCase(),
                        m -> m.getLabel(),
                        (a, b) -> a));

        // NOTE (merge judgment call, hunk 7): the incoming side also added phone-ownership
        // enforcement (resolveCitizenMobile + audit/anomaly calls) and page/sort/status query params
        // to this endpoint's SIGNATURE. That signature-level change sits OUTSIDE any marked conflict
        // hunk — our side had already independently rewritten this method to the simpler
        // `@RequestParam String phone` form with no pagination, so git's 3-way merge auto-resolved
        // the signature and surrounding ownership-check block to our side without flagging a conflict
        // (only this stream-building block disagreed enough to be marked). Retrofitting the
        // page/size/sortBy/sortDir/status/HttpServletRequest pagination here would require changing
        // that unconflicted signature too, which is out of scope for resolving the 10 marked hunks.
        // Keeping our (signature-compatible) stream here; the citizen pagination/sorting feature is
        // not reachable through this endpoint post-merge and should be revisited as a follow-up.
        List<Map<String, Object>> items = complaints.stream().map(c -> {
            // Public filing stores the RE as entityName and leaves bankId null, so resolving only
            // via bankId fell through to the subject — the citizen saw "ATM_DEBIT_CARD" in the
            // Regulated Entity Name column instead of their bank.
            String bankName = c.getEntityName() != null ? c.getEntityName() : "";
            if (bankName.isEmpty() && c.getBankId() != null) {
                bankName = bankRepository.findById(c.getBankId())
                        .map(b -> b.getName()).orElse("");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            // Portal Rejection (FR-G-013) complaints never get a complaintNumber, only a caseId —
            // falling back avoids leaving the ID column blank for every non-maintainable row.
            item.put("complaintId", c.getComplaintNumber() != null ? c.getComplaintNumber() : c.getCaseId());
            item.put("entityName", bankName.isEmpty() ? c.getSubject() : bankName);
            item.put("complaintDate", c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : "");
            item.put("status", c.getStatus() != null ? c.getStatus().toUpperCase() : "PENDING");
            item.put("comments", c.getDescription() != null ? c.getDescription().substring(0, Math.min(c.getDescription().length(), 50)) : "");

            // Closure details. The clause code is stored on the complaint; the human-readable label
            // lives in the clause master, so resolve it and fall back to the bare code.
            String clause = c.getClosureClause();
            item.put("closureClause", clause == null || clause.isBlank()
                    ? ""
                    : clauseLabels.getOrDefault(clause.toUpperCase(), clause));
            item.put("closureDate", c.getClosedAt() != null ? c.getClosedAt().format(fmt) : "");

            // UST111: "File an Appeal" must only be offered for closures under a clause the complainant
            // may appeal (15(1)(a)/15(1)(b)), not for every closed/non-maintainable complaint. Fails
            // closed — an unmapped or absent clause is not appealable.
            boolean appealable = false;
            try {
                appealable = appealClassificationService.isAppealable(
                        c, com.hrms.cms.entity.ClosureClauseMaster.AppealParty.COMPLAINANT);
            } catch (com.hrms.cms.service.AppealClassificationService.UnmappedClauseException ignored) {
                // No clause recorded yet, or clause absent from the master — not appealable.
            }
            item.put("appealable", appealable);

            // Letters are generated on demand, so the list advertises the download URLs rather than
            // stored files. The acknowledgement exists as soon as the complaint does; the closure
            // letter only once it has actually been closed. Non-Maintainable/portal-rejection
            // complaints have no complaintNumber, only a caseId — falling back avoids baking the
            // literal string "null" into the URL.
            String identifier = c.getComplaintNumber() != null ? c.getComplaintNumber() : c.getCaseId();
            String base = "/api/v1/complaints/" + identifier + "/letters/";
            item.put("acknowledgementLetterUrl", base + "acknowledgement");
            item.put("closureLetterUrl", c.getClosedAt() != null ? base + "closure" : "");
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Complaints retrieved");
        response.put("data", items);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Citizen-facing acknowledgement / closure letter as a PDF.
     *
     * <p>Separate from the staff {@code /api/v1/crpc/closure-letter} endpoint, which is role-guarded,
     * returns HTML, and renders the full Scheme template. This one is the citizen's copy: it is reachable
     * with the complaint number alone, because that is the only credential the My Complaints table has.
     *
     * <p>The identifier in the path may be a complaintNumber or, for Non-Maintainable/portal-rejection
     * complaints that never get a complaintNumber, a caseId instead (see the URL built in
     * {@link #getComplaintsByPhone}).
     */
    @GetMapping("/{identifier}/letters/{type}")
    public ResponseEntity<byte[]> downloadLetter(@PathVariable String identifier,
                                                 @PathVariable String type) {
        if (!"acknowledgement".equals(type) && !"closure".equals(type)) {
            return ResponseEntity.notFound().build();
        }

        Complaint c;
        try {
            c = complaintService.getByComplaintNumberOrCaseId(identifier);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }

        // A closure letter for a complaint that is still open would state a closure that has not
        // happened, so it is withheld until there is a closure date to print.
        if ("closure".equals(type) && c.getClosedAt() == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdf = complaintLetterPdfService.render(c, type);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + type + "-" + identifier + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> getComplaintDetail(
            @PathVariable String complaintNumber, HttpServletRequest request) {
        Complaint c;
        try {
            c = complaintService.getByComplaintNumber(complaintNumber);
        } catch (RuntimeException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Complaint not found");
            error.put("data", null);
            error.put("correlationId", UUID.randomUUID().toString());
            error.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        List<ComplaintTimeline> timeline = complaintService.getTimeline(c.getId());

        // Anyone with the complaint number can reach this endpoint by design (UST98/UST99 — open
        // tracking), but complaint numbers are a sequential counter (ComplaintNumberGeneratorService),
        // not a secret, so the PII below must stay masked unless the caller's citizen session proves
        // they are the complainant. Same ownership check performWithdrawal uses.
        // Staff are the other legitimate reader: CEPC's complaint-detail and create-complaint screens
        // populate the complainant block from this endpoint, so masking on a staff call blanks fields the
        // officer is handling the case from. RE staff are excluded inside canReveal (they work for the
        // entity being complained about).
        String sessionMobile = resolveCitizenMobile(request);
        boolean revealed = (sessionMobile != null && sessionMobile.equals(c.getComplainantPhone()))
                || piiMaskingService.canReveal(requestIdentityResolver.resolve(request));

        String bankName = c.getEntityName() != null ? c.getEntityName() : "";
        if (bankName.isEmpty() && c.getBankId() != null) {
            bankName = bankRepository.findById(c.getBankId())
                    .map(b -> b.getName())
                    .orElse("");
        }

        String categoryName = c.getCategoryName() != null ? c.getCategoryName() : "General";
        if ("General".equals(categoryName) && c.getCategoryId() != null) {
            categoryName = categoryRepository.findById(c.getCategoryId())
                    .map(cat -> cat.getName()).orElse("General");
        }

        String registeredAt = c.getCreatedAt() != null ? c.getCreatedAt().toString() : "";
        String slaDueDate = c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : "";

        List<ComplaintAttachment> attachments = complaintService.getAttachments(c.getId());
        List<Map<String, Object>> attachmentList = attachments.stream().map(a -> {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("id", a.getId());
            am.put("fileName", a.getOriginalName());
            am.put("name", a.getOriginalName());
            am.put("contentType", a.getContentType());
            am.put("size", a.getFileSize() != null ? formatFileSize(a.getFileSize()) : null);
            am.put("uploadedAt", a.getUploadedAt() != null ? a.getUploadedAt().toString() : null);
            return am;
        }).collect(Collectors.toList());

        // Compute citizen-facing 4-stage timeline (feeds detail.currentStage / detail.stages below).
        Map<String, Object> stageData = CitizenStageMapper.mapToStages(c, timeline);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", c.getId());
        detail.put("complaintId", c.getComplaintNumber());
        detail.put("complaintNumber", c.getComplaintNumber());
        detail.put("category", categoryName);
        detail.put("priority", c.getPriority() != null ? c.getPriority().toUpperCase() : "MEDIUM");
        detail.put("status", c.getStatus() != null ? c.getStatus().toUpperCase() : "NEW");
        detail.put("subject", c.getSubject());
        detail.put("description", c.getDescription());
        detail.put("complainantName", piiMaskingService.apply("complainantName", c.getComplainantName(), revealed));
        detail.put("complainantEmail", piiMaskingService.apply("complainantEmail", c.getComplainantEmail(), revealed));
        detail.put("complainantPhone", piiMaskingService.apply("complainantPhone", c.getComplainantPhone(), revealed));
        detail.put("complainantAddress", piiMaskingService.apply("complainantAddress", c.getComplainantAddress(), revealed));
        detail.put("complainantState", c.getComplainantState());
        detail.put("complainantDistrict", c.getComplainantDistrict());
        detail.put("complainantPincode", c.getComplainantPincode());
        detail.put("entityName", bankName);
        detail.put("entityType", c.getEntityType() != null ? c.getEntityType() : "BANK");
        detail.put("entityCategory", c.getEntityCategory());
        detail.put("bsrCode", c.getEntityBsrCode());
        detail.put("entityPincode", c.getEntityPincode());
        detail.put("entityState", c.getEntityState());
        detail.put("entityDistrict", c.getEntityDistrict());
        detail.put("entityCity", c.getEntityCity());
        detail.put("entityBranchName", c.getEntityBranchName());
        detail.put("entityBranchCategory", c.getEntityBranchCategory());
        detail.put("entityAddress", c.getEntityAddress());
        detail.put("cosmosCode", c.getCosmosCode());
        detail.put("schemeVersion", c.getSchemeVersion());
        detail.put("bankBranch", c.getBankBranch());
        detail.put("accountNumber", piiMaskingService.apply("accountNumber", c.getAccountNumber(), revealed));
        detail.put("amountInvolved", c.getAmountInvolved() != null ? c.getAmountInvolved() : 0);
        detail.put("reliefSought", c.getReliefSought());
        detail.put("filingType", c.getFilingType());
        detail.put("priorReComplaint", c.getPriorReComplaint());
        detail.put("reComplaintDate", c.getReComplaintDate() != null ? c.getReComplaintDate().toString() : null);
        detail.put("reComplaintReference", c.getReComplaintReference());
        detail.put("reRepliedAndDissatisfied", c.getReRepliedAndDissatisfied());
        detail.put("bankComplaintReference", c.getBankComplaintReference());
        detail.put("transactionDate", c.getBankComplaintDate() != null ? c.getBankComplaintDate().toString() : null);
        detail.put("assignedTeam", c.getAssignedOfficer() != null ? c.getAssignedOfficer() : "Unassigned");
        detail.put("assignedTo", c.getAssignedOfficer());
        detail.put("registeredAt", registeredAt);
        detail.put("createdAt", registeredAt);
        detail.put("filedAt", c.getFiledAt() != null ? c.getFiledAt().toString() : registeredAt);
        detail.put("slaDueDate", slaDueDate);
        detail.put("resolutionSummary", null);
        detail.put("resolvedAt", c.getResolvedAt() != null ? c.getResolvedAt().toString() : null);
        // New closure/stage fields
        detail.put("closureClause", c.getClosureClause());
        detail.put("closedAt", c.getClosedAt() != null ? c.getClosedAt().toString() : null);

        // ═══ Whether a withdrawal would be accepted (UST105 scenario 3) ═══
        //
        // The portal's withdrawal screen used to open its reason form for ANY complaint the tracker
        // could find, so a citizen with a closed or externally-forwarded complaint was walked through
        // choosing a reason, optionally attaching a document, and only then refused — after the work,
        // by the server, on a screen that had already promised the action.
        //
        // Published from here rather than re-derived in the browser on purpose: the exclusion list is
        // a Scheme rule (NON_WITHDRAWABLE_STATUSES below), and a second copy in TypeScript is a second
        // thing to forget when the list changes. The server stays the enforcement point either way —
        // this flag only stops the portal offering what the server will refuse.
        detail.put("withdrawable", isWithdrawable(c.getStatus()));

        // ═══ Whether this complaint has been appealed, and under which appeal number (UST111) ═══
        //
        // This response carried nothing about appeals at all, so the citizen tracker could not tell a
        // complaint that had been appealed from one that had not: it went on offering "File Appeal" after
        // a successful filing, and a citizen who took the offer travelled to the appeal screen only to be
        // refused there. The refusal was correct; continuing to offer the action was not.
        //
        // Read from the parent's OWN timeline rather than by querying APPEALS, on purpose. The appeal
        // record is AA-module state behind a role guard, and this endpoint also serves anonymous
        // track-by-reference — joining to it here would put appellant-side data on a public read. The
        // APPEAL_FILED timeline row (written by AppealWorkflowService.fileAppeal) is the complaint's own
        // history and carries the appeal number as its newValue, which is all the tracker needs.
        //
        // Deliberately NOT gated on isStaff: this is the citizen's own escalation, and telling the person
        // who filed an appeal that they filed one is the entire point.
        Optional<ComplaintTimeline> appealEvent = timeline.stream()
                .filter(t -> "APPEAL_FILED".equals(t.getAction()))
                // Latest wins: a complaint remanded and re-closed can be appealed again, and the CURRENT
                // appeal is the one the tracker must name.
                .max(Comparator.comparing(ComplaintTimeline::getPerformedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        detail.put("appealFiled", appealEvent.isPresent());
        detail.put("appealNumber", appealEvent.map(ComplaintTimeline::getNewValue).orElse(null));
        detail.put("appealFiledAt", appealEvent
                .map(t -> t.getPerformedAt() != null ? t.getPerformedAt().toString() : null)
                .orElse(null));

        detail.put("currentStage", stageData.get("currentStage"));
        detail.put("stages", stageData.get("stages"));

        // ═══ The milestone ladder (the coarse phase, as opposed to the fine-grained status) ═══
        //
        // The ladder is RBIO_STATUS_MASTER.MILESTONE_CODE, which is server-configured DATA and already
        // populated: REGISTER → ASSESSMENT → CONCILIATION → FORWARD → FINAL_DECISION, mapped from 34
        // status codes. Published from here rather than re-derived in the browser for the same reason
        // the withdrawable flag above is: a second copy of the mapping in TypeScript is a second thing
        // to forget, and the CEPC detail screen had no way to show which phase a complaint was in at
        // all — a reader could see "INFO_REQUESTED" but not that it meant Assessment.
        //
        // `milestone` on the complaint row itself is NULL on every pre-existing record (see
        // Complaint.milestone), so the CURRENT phase is resolved from the status through the same
        // master table rather than read off the column — which is what makes this work for the
        // thousands of migrated complaints too, instead of only for newly transitioned ones.
        detail.put("milestones", milestoneLadder());
        detail.put("milestone", currentMilestoneFor(c.getStatus()));
        detail.put("timeline", timeline.stream().map(t -> {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("fromStatus", t.getFromStatus());
            tm.put("toStatus", t.getToStatus());
            tm.put("action", t.getAction());
            tm.put("timestamp", t.getPerformedAt() != null ? t.getPerformedAt().toString() : "");
            tm.put("remarks", t.getRemarks());
            // The History panel consumes this embedded array, so omitting the actor left the audit
            // trail unattributable in the only place staff actually read it.
            tm.put("performedBy", t.getPerformedBy());
            tm.put("performedByRole", t.getPerformedByRole());
            // UST848: lets the UI separate what a person did from what the system derived, instead
            // of inferring it from the three different casings of "SYSTEM" in performedBy.
            tm.put("eventSource", t.getEventSource() != null ? t.getEventSource().name() : "MANUAL");
            return tm;
        }).collect(Collectors.toList()));
        detail.put("communications", List.of());
        detail.put("documents", attachmentList);
        detail.put("attachments", attachmentList);
        detail.put("triageSignal", c.getTriageSignal());
        detail.put("triageFlags", c.getTriageFlags());
        detail.put("eligibilityTimeline", c.getEligibilityTimeline());
        detail.put("hasRepresentative", c.getRepName() != null && !c.getRepName().isBlank());
        detail.put("representativeName", c.getRepName());
        detail.put("representativeEmail", c.getRepEmail());
        detail.put("representativePhone", c.getRepPhone());
        detail.put("eligibilityAnswers", readJsonMap(c.getEligibilityAnswersJson()));
        detail.put("wizardFormData", readJsonMap(c.getWizardFormDataJson()));
        detail.put("closureClause", c.getClosureClause());

        // UST111: tracker's "File an Appeal" action must only be offered for closures under a
        // clause the complainant may appeal (15(1)(a)/15(1)(b)) — same rule as the history list
        // (see appealable above). Fails closed on an unmapped or absent clause.
        boolean detailAppealable = false;
        try {
            detailAppealable = appealClassificationService.isAppealable(
                    c, com.hrms.cms.entity.ClosureClauseMaster.AppealParty.COMPLAINANT);
        } catch (com.hrms.cms.service.AppealClassificationService.UnmappedClauseException ignored) {
            // No clause recorded yet, or clause absent from the master — not appealable.
        }
        detail.put("appealable", detailAppealable);
        detail.put("proposedAction", c.getProposedAction());
        detail.put("proposedClause", c.getProposedClause());
        detail.put("forwardedOfficeCode", c.getForwardedOfficeCode());
        detail.put("forwardedOfficeName", c.getForwardedOfficeCode() != null
                ? officeCodeMasterRepository.findByOfficeCodeAndIsActiveTrue(c.getForwardedOfficeCode())
                        .map(o -> o.getOfficeName()).orElse(c.getForwardedOfficeCode())
                : null);
        detail.put("preForwardOfficer", c.getPreForwardOfficer());
        detail.put("preForwardRole", c.getPreForwardRole());
        detail.put("closureClauseDescription", c.getClosureClauseDescription());
        detail.put("complaintStatusOnPortal", c.getComplaintStatusOnPortal());
        detail.put("speakingOrderGenerated", c.getSpeakingOrderGenerated());
        detail.put("gistOfCase", c.getGistOfCase());
        detail.put("gistOfCaseRegional", c.getGistOfCaseRegional());
        detail.put("department", c.getDepartment());
        detail.put("workflowStage", c.getWorkflowStage());
        detail.put("piiMasked", !revealed);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", detail);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * UST875: explicit, audited reveal of complainant PII.
     *
     * Deliberately a POST even though it reads data — a reveal writes an audit row and is not
     * safely repeatable-without-trace, and a GET would end up in browser history and proxy logs.
     */
    @PostMapping("/{complaintNumber}/reveal-pii")
    public ResponseEntity<Map<String, Object>> revealPii(@PathVariable String complaintNumber,
                                                        @RequestBody(required = false) Map<String, String> body,
                                                        HttpServletRequest request) {
        RequestIdentity identity = requestIdentityResolver.resolve(request);
        if (identity == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    errorBody("UNAUTHENTICATED", "Sign in to view complainant details."));
        }

        Complaint complaint;
        try {
            complaint = complaintService.getByComplaintNumber(complaintNumber);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    errorBody("NOT_FOUND", "Complaint number not found, please check and try again."));
        }

        String justification = body == null ? null : body.get("justification");
        try {
            Map<String, Object> revealed = piiRevealService.reveal(
                    complaint, identity, justification, "COMPLAINT_DETAIL", request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Complainant details revealed and recorded");
            response.put("data", revealed);
            response.put("correlationId", UUID.randomUUID().toString());
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.ok(response);
        } catch (PiiRevealService.RevealNotPermittedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    errorBody("REVEAL_NOT_PERMITTED", e.getMessage()));
        } catch (PiiRevealService.JustificationRequiredException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    errorBody("JUSTIFICATION_REQUIRED", e.getMessage()));
        }
    }

    // ═══ UST105/107/108: Withdraw complaint ═══

    /**
     * The statuses in which a withdrawal is REFUSED. A DENY-list, deliberately.
     *
     * <p>This was an ALLOW-list of {pending,new,assigned,in_progress,under_review,escalated}, which
     * refused withdrawal in five statuses that are plainly still active — {@code info_requested},
     * {@code forwarded}, {@code reviewer_review}, {@code incharge_review}, {@code awaiting_closure}.
     * A citizen whose complaint happened to be sitting with a Reviewer could not withdraw it, which the
     * Scheme does not permit the product to decide. Every new active status added to the workflow since
     * also silently joined the refusal set, because an allow-list fails closed on anything it has not
     * heard of — and the workflow has gained statuses (adjudication, conciliation, re_responded,
     * hearing_scheduled, sent_back, …) in every wave since this list was written.
     *
     * <p>The Scheme excludes exactly THREE outcomes: Complaint Closed, Sent to other Department, Sent to
     * other Regulatory Bodies. Those map onto the stored vocabulary as {@code closed} and
     * {@code forwarded_external} / {@code sent_to_other} — the single status
     * {@code forwarded_external} carries BOTH forward kinds (CepcWorkflowService:529-565 sets it for
     * FORWARD_TO_REGULATORY_BODY and FORWARD_TO_OTHER_RBI_DEPT alike, distinguished only by
     * workflowStage), and {@code sent_to_other} is the RBIO inter-office equivalent.
     *
     * <p>The remaining four entries are not Scheme exclusions but arithmetic ones: a complaint that has
     * already REACHED a settled outcome has nothing left to withdraw, and {@code withdrawn} above all
     * must not be withdrawable twice — a second call would overwrite the first reason and date, losing
     * the citizen's original statement. {@code resolved}/{@code rejected} are the other two settled
     * codes in {@code cms.citizen-tracking.settled-statuses}; {@code adjudicated} and
     * {@code conciliated} are their RBIO-ladder equivalents (RbioStatusMasterSeeder:101-106 marks all of
     * these {@code isClosed=Y}).
     *
     * <p>Derived from the real vocabulary, not from guesswork: {@code SELECT DISTINCT status FROM
     * COMPLAINTS} (22 values), every {@code setStatus("…")} literal in the backend (26 values), and the
     * RBIO_STATUS_MASTER legacy codes. Everything NOT named here — including statuses this list has
     * never heard of — is withdrawable, which is the correct default for a citizen right.
     */
    private static final Set<String> NON_WITHDRAWABLE_STATUSES = Set.of(
            // ── The three the Scheme excludes ──
            "closed",              // Complaint Closed
            "forwarded_external",  // Sent to other Department AND Sent to other Regulatory Bodies
            "sent_to_other",       // Sent to Other Office (RBIO inter-office transfer)
            // ── Already settled: there is no live complaint left to withdraw ──
            "withdrawn",           // must never be withdrawn twice — it would overwrite the first reason
            "resolved",
            "rejected",
            "adjudicated",
            "conciliated");

    /**
     * The single answer to "may this complaint be withdrawn?", used by BOTH the withdraw handler and
     * the tracker payload's {@code withdrawable} flag so the portal cannot offer what the server
     * refuses.
     */
    private static boolean isWithdrawable(String status) {
        String normalised = status != null ? status.toLowerCase() : "";
        return !NON_WITHDRAWABLE_STATUSES.contains(normalised);
    }

    /**
     * The milestone ladder in display order, from RBIO_STATUS_MASTER.
     *
     * <p>DATA, not a literal: the codes and their order come from the same master table the status
     * filter tabs come from, so the phase a complaint is said to be in cannot drift from the phase the
     * rest of the product computes. Each entry carries the code, a display label and a translation key
     * so the UI need not hold a parallel mapping.
     *
     * <p>Order is MILESTONE_CODE's first appearance by DISPLAY_ORDER, which is the sequence the ladder
     * is actually walked in (REGISTER → ASSESSMENT → CONCILIATION → FORWARD → FINAL_DECISION) rather
     * than alphabetical.
     */
    private List<Map<String, Object>> milestoneLadder() {
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        for (RbioStatusMaster s : rbioStatusMasterRepository.findByIsActiveOrderByDisplayOrderAsc("Y")) {
            String code = s.getMilestoneCode();
            if (code == null || code.isBlank() || byCode.containsKey(code)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("label", MILESTONE_LABELS.getOrDefault(code, code));
            m.put("translationKey", "milestone." + code.toLowerCase(Locale.ROOT));
            byCode.put(code, m);
        }
        return new ArrayList<>(byCode.values());
    }

    /**
     * The milestone a complaint is currently in, resolved from its STATUS through the master table.
     *
     * <p>Not read from {@code COMPLAINTS.MILESTONE}: that column is null on every pre-existing row by
     * design (back-filling it would mean inventing which phase a closed complaint was in), so reading
     * it would leave the ladder blank for all but newly transitioned complaints. The status is always
     * present, and the status→milestone mapping is the same one the ladder is built from.
     */
    private String currentMilestoneFor(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String wanted = status.trim().toUpperCase(Locale.ROOT);
        return rbioStatusMasterRepository.findByIsActiveOrderByDisplayOrderAsc("Y").stream()
                .filter(s -> wanted.equalsIgnoreCase(s.getStatusCode())
                        || wanted.equalsIgnoreCase(s.getLegacyValue()))
                .map(RbioStatusMaster::getMilestoneCode)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Display labels for the milestone codes.
     *
     * <p>English only and held here rather than in TRANSLATION_KEYS because the ladder also carries a
     * translationKey per entry, so a localised UI uses that and this is the fallback for callers that
     * do not translate (and for the raw API).
     */
    private static final Map<String, String> MILESTONE_LABELS = Map.of(
            "REGISTER", "Register",
            "ASSESSMENT", "Assessment",
            "CONCILIATION", "Conciliation",
            "FORWARD", "Forward",
            "FINAL_DECISION", "Final decision");

    /**
     * The formats a COMPLAINANT may attach to a withdrawal (UST107 / FR-G-036).
     *
     * <p>Deliberately narrower than {@code cms.attachments.allowed-types}, which is the officer set.
     * This is the same set the citizen complaint-filing wizard offers
     * (cms-portal-frontend file-validator.ts), so the portal gives one answer to "what can I
     * attach?" wherever a citizen is asked for a document.
     *
     * <p>Matched on EXTENSION, mirroring the client gate, because the browser reports an empty or
     * generic content type for plenty of legitimate files. The magic-byte sniff in
     * FileUploadValidator still runs afterwards, so this is a filter on what the citizen can SEE and
     * correct, not the only check.
     */
    private static final Set<String> WITHDRAWAL_DOC_EXTENSIONS =
            Set.of("pdf", "doc", "jpg", "jpeg", "png");

    private static boolean isAllowedWithdrawalDocument(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return false;
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1)
                .toLowerCase(java.util.Locale.ROOT);
        return WITHDRAWAL_DOC_EXTENSIONS.contains(extension);
    }

    /**
     * JSON arm — a withdrawal with no supporting documents.
     *
     * <p>{@code consumes} is now explicit on both arms. Without it Spring cannot tell the two apart
     * and the multipart request below would bind {@code @RequestBody Map} against a multipart body
     * and fail as a malformed request, which is exactly what a citizen attaching a document would
     * have seen.
     */
    @PostMapping(value = "/{complaintNumber}/withdraw",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<Map<String, Object>> withdrawComplaint(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return performWithdrawal(complaintNumber, body, List.of(), request);
    }

    /**
     * MULTIPART arm — a withdrawal that carries the supporting documents UST107 scenario 2 offers.
     *
     * <p>Separate mapping rather than one method taking an optional part, because the JSON arm has
     * ~40 existing callers (the portal, the e2e suite, the citizen app) whose request shape must not
     * change at all. The business rules are shared: both arms delegate to
     * {@link #performWithdrawal}, so the eligibility, ownership, reason and audit behaviour cannot
     * drift between them.
     *
     * <p>The documents are stored only AFTER every refusal has been cleared, so a rejected
     * withdrawal leaves nothing behind.
     */
    @PostMapping(value = "/{complaintNumber}/withdraw", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> withdrawComplaintWithDocuments(
            @PathVariable String complaintNumber,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "remarks", required = false) String remarks,
            @RequestParam(value = "documents", required = false) List<MultipartFile> documents,
            HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        if (reason != null) body.put("reason", reason);
        if (remarks != null) body.put("remarks", remarks);

        return performWithdrawal(complaintNumber, body,
                documents == null ? List.of() : documents, request);
    }

    private ResponseEntity<Map<String, Object>> performWithdrawal(
            String complaintNumber,
            Map<String, Object> body,
            List<MultipartFile> documents,
            HttpServletRequest request) {

        // Validate: complaint exists
        Complaint complaint;
        try {
            complaint = complaintService.getByComplaintNumber(complaintNumber);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Complaint not found",
                    "timestamp", LocalDateTime.now().toString()));
        }

        // UST105: withdrawal is a citizen-only action (staff use the workflow endpoints),
        // so it always requires a verified citizen session owning the complaint.
        String sessionMobile = resolveCitizenMobile(request);
        if (sessionMobile == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    errorBody("SESSION_EXPIRED", "Your session has expired. Please verify your mobile number again."));
        }
        if (!sessionMobile.equals(complaint.getComplainantPhone())) {
            auditService.logActionAsync(complaintNumber, "WITHDRAW_DENIED", sessionMobile, "CITIZEN",
                    "Attempted to withdraw a complaint filed by another mobile number", null, null, null);
            anomalyDetectionService.recordOwnershipDenial(sessionMobile, complaintNumber,
                    "Attempted to withdraw a complaint filed by another mobile number", request);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    errorBody("FORBIDDEN", "You can only withdraw complaints filed with your own mobile number."));
        }

        // Validate: reason is non-blank and <=500 chars
        String reason = body.get("reason") != null ? body.get("reason").toString().trim() : "";
        if (reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Reason for withdrawal is required.",
                    "timestamp", LocalDateTime.now().toString()));
        }
        if (reason.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Withdrawal reason must not exceed 500 characters.",
                    "timestamp", LocalDateTime.now().toString()));
        }

        // Validate: status allows withdrawal
        if (!isWithdrawable(complaint.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "This complaint cannot be withdrawn in its current status (" + complaint.getStatus().toUpperCase() + ").",
                    "timestamp", LocalDateTime.now().toString()));
        }

        // Build combined reason with optional remarks
        String remarks = body.get("remarks") != null ? body.get("remarks").toString().trim() : "";
        String fullReason = remarks.isEmpty() ? reason : reason + " — " + remarks;

        // ═══ Store the supporting documents BEFORE the status flips ═══
        //
        // Order matters in both directions. Storing first means a document the validator refuses
        // (wrong format, oversize, over the per-record cap) aborts the whole withdrawal, so the
        // citizen is told their attachment was rejected while the complaint is still live and they
        // can retry — rather than being left with a withdrawn complaint and a silently dropped
        // document, which is unrecoverable because a withdrawn complaint cannot be withdrawn again.
        //
        // The refusal is a 400 with the validator's own citizen-facing message, matching the shape
        // every other refusal on this endpoint uses.
        List<ComplaintAttachment> storedDocuments = new ArrayList<>();
        for (MultipartFile document : documents) {
            if (document == null || document.isEmpty()) {
                continue;
            }
            // The CITIZEN format gate, enforced here rather than left to FileStorageService.
            //
            // cms.attachments.allowed-types is the OFFICER set and legitimately includes xls/xlsx/
            // csv/zip and the media types — a caseworker attaching a bank statement workbook to a
            // case file is normal. A complainant withdrawing a complaint is not that caller: the
            // portal offers them exactly pdf/doc/jpg/jpeg/png (the same set the complaint-filing
            // wizard offers), and UST107 scenario 3 requires the unsupported ones to be refused.
            //
            // Enforced server-side because the client gate is bypassable by a direct call, and a
            // format filter that exists only in the browser is not a control at all.
            if (!isAllowedWithdrawalDocument(document.getOriginalFilename())) {
                log.warn("Rejected withdrawal document '{}' for {}: unsupported format for a citizen"
                        + " withdrawal (permitted: {})",
                        document.getOriginalFilename(), complaintNumber, WITHDRAWAL_DOC_EXTENSIONS);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid file type or size, please upload a valid document.",
                        "timestamp", LocalDateTime.now().toString()));
            }
            try {
                storedDocuments.add(fileStorageService.handleSingleUpload(
                        document, complaintNumber, complaint.getId(),
                        complaint.getComplainantPhone(), ComplaintAttachment.SOURCE_COMPLAINANT,
                        "WITHDRAWAL_SUPPORT"));
            } catch (Exception e) {
                log.warn("Rejected withdrawal document '{}' for {}: {}",
                        document.getOriginalFilename(), complaintNumber, e.getMessage());
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", e.getMessage() != null ? e.getMessage()
                                : "Invalid file type or size, please upload a valid document.",
                        "timestamp", LocalDateTime.now().toString()));
            }
        }

        // Set withdrawal fields
        String previousStatus = complaint.getStatus();
        complaint.setStatus("withdrawn");
        complaint.setWithdrawalReason(fullReason);
        complaint.setWithdrawalDate(LocalDateTime.now());
        complaint.setWithdrawnBy(
                complaint.getComplainantPhone() != null && !complaint.getComplainantPhone().isBlank()
                        ? complaint.getComplainantPhone() : "citizen");

        // Save
        complaintService.updateMaintainability(complaint);

        // ═══ The audit trail ═══
        //
        // The documents are NAMED in the timeline remark, not merely stored against the complaint.
        // "The audit trail must link to the document submitted with the withdrawal" cannot be
        // satisfied by a row in COMPLAINT_ATTACHMENTS alone: a caseworker reading /history has no way
        // to tell which of a complaint's attachments arrived with the withdrawal and which were filed
        // with the original complaint.
        String documentNote = storedDocuments.isEmpty() ? ""
                : " Documents: " + storedDocuments.stream()
                        .map(a -> a.getOriginalName() + " (/api/files/download/" + a.getId() + ")")
                        .collect(Collectors.joining(", "));

        complaintService.addTimeline(complaint.getId(), "withdrawn", complaint.getWithdrawnBy(),
                "Complaint withdrawn by complainant. Reason: " + fullReason + documentNote,
                previousStatus, "withdrawn");

        // ═══ Tell the officers working the case (UST108 / FR-G-037) ═══
        //
        // This replaced a comment that said no event was published and left it at that. The
        // consequence was measurable: across every complaint already in status `withdrawn`,
        // IN_APP_NOTIFICATIONS and COMMUNICATION_OUTBOX held not one withdrawal row, so the officer
        // holding the case and the entity's NO/PNO kept working a complaint the citizen had
        // abandoned.
        //
        // Recipients come from configuration (notification.recipients.withdrawal), and raiseEvent
        // expands the per-complaint placeholders — so the NO/PNO reached are the ones for THIS
        // complaint's entity, and no other entity's officers can be addressed.
        //
        // Every mandatory field FR-G-037 scenario 3 names is in the message itself rather than only
        // in a linked record: complaint number, complainant name, withdrawal date, reason, and the
        // download link for each document. A notification whose content lives behind a click is not
        // one the recipient can act on from the bell.
        notifyWithdrawal(complaint, fullReason, storedDocuments);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", complaint.getComplaintNumber());
        data.put("status", "WITHDRAWN");
        data.put("withdrawalDate", complaint.getWithdrawalDate().toString());
        data.put("message", "Your complaint has been withdrawn successfully. You will receive a confirmation via SMS/Email.");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Complaint withdrawn successfully");
        response.put("data", data);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Raises the withdrawal alert, and never lets a notification problem undo an accepted withdrawal.
     *
     * <p>The try/catch is the point of the separate method. The withdrawal is already committed by
     * the time this runs; if the recipient resolver, the channel matrix or the message broker is
     * unavailable, propagating would turn a successful, persisted withdrawal into a 500 and invite a
     * retry that must then be refused as "already withdrawn". The citizen's action stands and the
     * failure is logged at WARN so it is visible without being fatal.
     */
    private void notifyWithdrawal(Complaint complaint, String reason,
                                  List<ComplaintAttachment> documents) {
        try {
            String complainant = complaint.getComplainantName() != null
                    && !complaint.getComplainantName().isBlank()
                    ? complaint.getComplainantName() : "the complainant";

            // Date-only: the officer needs the DAY of withdrawal, and a full timestamp with
            // nanoseconds in a bell message reads as a machine log rather than a message.
            String withdrawalDate = complaint.getWithdrawalDate() != null
                    ? complaint.getWithdrawalDate().toLocalDate().toString()
                    : LocalDateTime.now().toLocalDate().toString();

            // Omitted entirely when there are none, rather than rendered as an empty "Documents:"
            // heading or — worse — the literal "null" the no-documents case explicitly forbids.
            String documentList = documents.isEmpty() ? ""
                    : " Supporting documents: " + documents.stream()
                            .map(a -> a.getOriginalName() + " /api/files/download/" + a.getId())
                            .collect(Collectors.joining(", ")) + ".";

            String title = "Complaint " + complaint.getComplaintNumber() + " withdrawn by complainant";
            String message = "Complaint " + complaint.getComplaintNumber()
                    + " has been withdrawn by " + complainant
                    + " on " + withdrawalDate
                    + ". Reason for withdrawal: " + reason + "."
                    + documentList;

            notificationService.raiseEvent(
                    notificationConfigService.withdrawalRecipients(),
                    "COMPLAINT_WITHDRAWN",
                    title,
                    message,
                    complaint.getComplaintNumber(),
                    "COMPLAINT",
                    "/complaints/" + complaint.getComplaintNumber(),
                    complaint);
        } catch (Exception e) {
            log.warn("Withdrawal of {} succeeded but its notification could not be raised: {}",
                    complaint.getComplaintNumber(), e.getMessage(), e);
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentComplaints(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(limit, 50);
        List<Complaint> complaints = complaintService.getAllComplaintsPaged(PageRequest.of(0, limit)).getContent();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");

        List<Map<String, Object>> items = complaints.stream().map(c -> {
            String bankName = "";
            if (c.getBankId() != null) {
                bankName = bankRepository.findById(c.getBankId())
                        .map(b -> b.getName()).orElse("");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("complaintNumber", c.getComplaintNumber());
            item.put("subject", c.getSubject());
            item.put("entityName", bankName);
            item.put("complainantName", c.getComplainantName());
            item.put("status", c.getStatus());
            item.put("date", c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : "");
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Recent complaints retrieved");
        response.put("data", items);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    // The officer-facing comment threads, send-for-approval and office-head-decision used to be served
    // from here too, on the same paths as ComplaintCommentController and CepcSendForApprovalController.
    // Two handlers for one path is an Ambiguous mapping: it aborted startup rather than picking a winner.
    // Those dedicated controllers own the routes now - they role-guard the action and go through the
    // workflow/transfer services, which these copies did not. This class keeps only the citizen-facing API.
    //
    // MERGE NOTE: this used to also be where a simpler @PostMapping("/{complaintNumber}/withdraw")
    // lived (phone-in-body ownership check, no documents, no NON_WITHDRAWABLE_STATUSES deny-list). It
    // is superseded by withdrawComplaint/withdrawComplaintWithDocuments above, which own this same
    // path split across a JSON arm and a MULTIPART arm — keeping both the old and the new handler
    // would be an ambiguous mapping on "/{complaintNumber}/withdraw" and fail application startup, so
    // the old handler was removed rather than merged. complaintService.withdrawComplaint(..) was its
    // only caller.

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    /**
     * Round-robin assignment scoped to a specific office. Falls back to the unscoped
     * role-wide round robin if no officer's OfficerAvailability record matches the office.
     */
    private String assignOfficerByRoleAndOffice(String role, String officeCode) {
        List<Map<String, Object>> officers = keycloakUserService.getUsersByRole(role);
        List<Map<String, Object>> officeMatched = new ArrayList<>();
        for (Map<String, Object> officer : officers) {
            String userId = (String) officer.get("userId");
            officerAvailabilityRepository.findByUserIdAndRole(userId, role).ifPresent(oa -> {
                if (officeCode != null && officeCode.equals(oa.getOfficeCode())) {
                    officeMatched.add(officer);
                }
            });
        }
        if (officeMatched.isEmpty()) {
            return complaintRoutingService.assignOfficerByRole(role);
        }
        String picked = complaintRoutingService.assignOfficerByRole(role);
        boolean pickedInOffice = officeMatched.stream().anyMatch(o -> picked.equals(o.get("userId")));
        return pickedInOffice ? picked : (String) officeMatched.get(0).get("userId");
    }
}
