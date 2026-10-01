package com.hrms.cms.controller;

import com.hrms.cms.config.DuplicateCheckProperties;
import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.service.CepcAuditService;
import com.hrms.cms.service.CitizenSessionService;
import com.hrms.cms.service.CitizenStageMapper;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.AnomalyDetectionService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@Slf4j
public class ComplaintApiV1Controller {

    private final ComplaintService complaintService;
    private final BankRepository bankRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final ComplaintRepository complaintRepository;
    private final IntakeTriageService triageService;
    private final CitizenSessionService citizenSessionService;
    private final CepcAuditService auditService;
    private final DuplicateCheckProperties duplicateCheckProps;
    private final Validator validator;
    private final RequestIdentityResolver requestIdentityResolver;
    private final PiiMaskingService piiMaskingService;
    private final PiiRevealService piiRevealService;
    private final AnomalyDetectionService anomalyDetectionService;

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
        if (request.get("reReplyDate") != null && !request.get("reReplyDate").toString().isBlank()) {
            req.setReReplyDate(java.time.LocalDate.parse(request.get("reReplyDate").toString()));
        }
        if (request.get("declarationAccepted") != null) {
            req.setDeclarationAccepted(Boolean.valueOf(request.get("declarationAccepted").toString()));
        }

        // D7: the wizard validates nine representative fields as mandatory and then the payload was
        // discarded here, leaving an officer no way to reach or verify the representative.
        if (request.get("hasAuthRep") != null) {
            req.setHasAuthRep(Boolean.valueOf(request.get("hasAuthRep").toString()));
        }
        if (request.get("throughAdvocate") != null) {
            req.setThroughAdvocate(Boolean.valueOf(request.get("throughAdvocate").toString()));
        }
        req.setRepName(asString(request.get("repName")));
        req.setRepPhone(asString(request.get("repPhone")));
        req.setRepEmail(asString(request.get("repEmail")));
        req.setRepAddress(asString(request.get("repAddress")));
        req.setRepState(asString(request.get("repState")));
        req.setRepDistrict(asString(request.get("repDistrict")));
        req.setRepCity(asString(request.get("repCity")));
        req.setRepPincode(asString(request.get("repPincode")));

        // Resolve category name to ID
        if (request.get("category") != null) {
            String categoryName = request.get("category").toString();
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

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("complaintId", c.getComplaintNumber());
        ack.put("status", "REGISTERED");
        ack.put("registeredAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : LocalDateTime.now().toString());
        ack.put("slaDueDate", c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : LocalDateTime.now().plusDays(30).toString());
        ack.put("acknowledgementMessage", "Your complaint has been registered successfully. Use the reference number to track status.");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Complaint registered successfully");
        response.put("data", ack);
        response.put("correlationId", UUID.randomUUID().toString());
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
     * UST76: duplicate pre-check run before a public complaint is submitted.
     * Matches on phone OR email for the same entity + category within the configured lookback
     * window, ignoring complaints that have reached a terminal status.
     */
    @PostMapping("/check-duplicate")
    public ResponseEntity<Map<String, Object>> checkDuplicate(@RequestBody Map<String, Object> request) {
        String phone = trimToNull(request.get("phone"));
        String email = trimToNull(request.get("email"));

        if (phone == null && email == null) {
            return ResponseEntity.badRequest().body(
                    errorBody("MISSING_IDENTIFIER", "A mobile number or email address is required to check for duplicates."));
        }

        Long bankId = resolveBankId(trimToNull(request.get("entityName")));
        Long categoryId = resolveCategoryId(trimToNull(request.get("category")));

        LocalDateTime since = LocalDateTime.now().minusDays(duplicateCheckProps.getLookbackDays());
        List<Complaint> matches = complaintRepository.findPotentialDuplicates(
                phone, email, bankId, categoryId, duplicateCheckProps.getTerminalStatuses(), since);

        Map<String, Object> data = new LinkedHashMap<>();
        if (matches.isEmpty()) {
            data.put("duplicate", false);
        } else {
            Complaint match = matches.get(0);
            // Phone is the stronger signal, so report it when it is what actually matched.
            boolean phoneMatched = phone != null && phone.equals(match.getComplainantPhone());
            data.put("duplicate", true);
            data.put("matchedOn", phoneMatched ? "phone" : "email");
            data.put("complaintNumber", match.getComplaintNumber());
            data.put("status", match.getStatus() != null ? match.getStatus().toUpperCase() : null);
            data.put("filedAt", match.getCreatedAt() != null ? match.getCreatedAt().toString() : null);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Duplicate check completed");
        response.put("data", data);
        // The Angular client reads these off the root, so mirror them for backward compatibility.
        response.putAll(data);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    private String trimToNull(Object value) {
        if (value == null) return null;
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** Entity names come from the BANKS master table — an unknown name widens the check rather than failing it. */
    private Long resolveBankId(String entityName) {
        if (entityName == null) return null;
        return bankRepository.findAll().stream()
                .filter(b -> entityName.equalsIgnoreCase(b.getName()) || entityName.equalsIgnoreCase(b.getCode()))
                .map(Bank::getId)
                .findFirst()
                .orElse(null);
    }

    private Long resolveCategoryId(String category) {
        if (category == null) return null;
        return categoryRepository.findFirstByNameIgnoreCase(category)
                .map(cat -> cat.getId())
                .orElse(null);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getComplaintsByPhone(
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {

        // UST98: the complaint list is scoped to the authenticated citizen's own mobile.
        // The client-supplied phone is never trusted — it is only checked for mismatch.
        String sessionMobile = resolveCitizenMobile(request);
        if (sessionMobile == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    errorBody("SESSION_EXPIRED", "Your session has expired. Please verify your mobile number again."));
        }
        if (phone != null && !phone.isBlank() && !phone.equals(sessionMobile)) {
            auditService.logActionAsync("N/A", "TRACK_LIST_DENIED", sessionMobile, "CITIZEN",
                    "Attempted to list complaints for a different mobile number", null, null, null);
            // UST873: counted, so repeated probing for other people's complaints raises an alert
            // instead of only leaving an audit row nobody aggregates.
            anomalyDetectionService.recordOwnershipDenial(sessionMobile, null,
                    "Attempted to list complaints for a different mobile number", request);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    errorBody("FORBIDDEN", "You can only view complaints filed with your own mobile number."));
        }
        phone = sessionMobile;

        // Cap page size at 50
        size = Math.min(size, 50);

        // Validate sortBy to prevent injection — only allow known columns
        Set<String> allowedSortFields = Set.of("createdAt", "status", "complaintNumber", "priority", "closedAt");
        if (!allowedSortFields.contains(sortBy)) {
            sortBy = "createdAt";
        }

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(Sort.Direction.ASC, sortBy)
                : Sort.by(Sort.Direction.DESC, sortBy);
        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<Complaint> complaintPage;
        if (STATUS_FILTER_OPEN.equalsIgnoreCase(status)) {
            // "Open" is a SET, not a stored status. Filtering on it as an exact value returned zero rows
            // for every citizen, because no complaint is ever stored with status 'open'.
            complaintPage = complaintRepository.findByComplainantPhoneAndStatusNotIn(
                    phone,
                    settledStatuses.stream().map(String::toLowerCase).toList(),
                    pageable);
        } else if (status != null && !status.isBlank()) {
            complaintPage = complaintRepository.findByComplainantPhoneAndStatus(phone, status.toLowerCase(), pageable);
        } else {
            complaintPage = complaintRepository.findByComplainantPhone(phone, pageable);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");

        List<Map<String, Object>> items = complaintPage.getContent().stream().map(c -> {
            String bankName = "";
            if (c.getBankId() != null) {
                bankName = bankRepository.findById(c.getBankId())
                        .map(b -> b.getName()).orElse("");
            }

            String categoryName = "General";
            if (c.getCategoryId() != null) {
                categoryName = categoryRepository.findById(c.getCategoryId())
                        .map(cat -> cat.getName()).orElse("General");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("complaintNumber", c.getComplaintNumber());
            item.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            item.put("createdAtFormatted", c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : "");
            item.put("categoryName", categoryName);
            item.put("entityName", bankName.isEmpty() ? c.getSubject() : bankName);
            item.put("status", c.getStatus() != null ? c.getStatus().toUpperCase() : "PENDING");
            item.put("closureClause", c.getClosureClause());
            item.put("closedAt", c.getClosedAt() != null ? c.getClosedAt().toString() : null);
            item.put("closedAtFormatted", c.getClosedAt() != null ? c.getClosedAt().format(fmt) : null);
            item.put("priority", c.getPriority() != null ? c.getPriority().toUpperCase() : "MEDIUM");
            // Keep legacy field for backward compat
            item.put("complaintId", c.getComplaintNumber());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> paginatedData = new LinkedHashMap<>();
        paginatedData.put("content", items);
        paginatedData.put("totalElements", complaintPage.getTotalElements());
        paginatedData.put("totalPages", complaintPage.getTotalPages());
        paginatedData.put("currentPage", complaintPage.getNumber());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Complaints retrieved");
        response.put("data", paginatedData);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> getComplaintDetail(@PathVariable String complaintNumber,
                                                                 HttpServletRequest request) {
        Complaint c;
        try {
            c = complaintService.getByComplaintNumber(complaintNumber);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    errorBody("NOT_FOUND", "Complaint number not found, please check and try again."));
        }

        // UST98/UST99: tracking by reference number stays public, but complainant PII is only
        // returned in full to the owning citizen (verified session).
        //
        // UST875: staff no longer get full PII by simply being staff. Presence of an Authorization
        // header used to be the whole test, so any non-empty string unmasked a complainant's name
        // and phone. Staff now receive masked values and must ask for a reveal, which is audited.
        String sessionMobile = resolveCitizenMobile(request);
        boolean isOwner = sessionMobile != null && sessionMobile.equals(c.getComplainantPhone());
        RequestIdentity identity = requestIdentityResolver.resolve(request);
        boolean isStaff = identity != null && !identity.isRe();
        boolean piiAllowed = isOwner;

        auditService.logActionAsync(complaintNumber, "TRACK_VIEWED",
                isOwner ? sessionMobile : (isStaff ? "STAFF" : "ANONYMOUS"),
                isOwner ? "CITIZEN" : (isStaff ? "STAFF" : "PUBLIC"),
                "Complaint status viewed via tracker", null, null, null);
        List<ComplaintTimeline> timeline = complaintService.getTimeline(c.getId());

        String bankName = "";
        if (c.getBankId() != null) {
            bankName = bankRepository.findById(c.getBankId())
                    .map(b -> b.getName())
                    .orElse("");
        }

        String categoryName = "General";
        if (c.getCategoryId() != null) {
            categoryName = categoryRepository.findById(c.getCategoryId())
                    .map(cat -> cat.getName()).orElse("General");
        }

        String registeredAt = c.getCreatedAt() != null ? c.getCreatedAt().toString() : "";
        String slaDueDate = c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : "";

        // Compute citizen-facing 4-stage timeline
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
        detail.put("complainantName",
                piiMaskingService.apply("complainantName", c.getComplainantName(), piiAllowed));
        detail.put("complainantPhone",
                piiMaskingService.apply("complainantPhone", c.getComplainantPhone(), piiAllowed));
        detail.put("complainantEmail",
                piiMaskingService.apply("complainantEmail", c.getComplainantEmail(), piiAllowed));
        detail.put("accountNumber",
                piiMaskingService.apply("accountNumber", c.getAccountNumber(), piiAllowed));
        detail.put("piiMasked", !piiAllowed && piiMaskingService.isMaskingEnabled());
        detail.put("canRevealPii", piiMaskingService.canReveal(identity));
        detail.put("entityName", bankName);
        detail.put("entityType", "BANK");
        detail.put("amountInvolved", 0);
        detail.put("transactionDate", c.getBankComplaintDate() != null ? c.getBankComplaintDate().toString() : null);
        detail.put("assignedTeam", c.getAssignedOfficer() != null ? c.getAssignedOfficer() : "Unassigned");
        detail.put("assignedTo", c.getAssignedOfficer());
        detail.put("registeredAt", registeredAt);
        detail.put("createdAt", registeredAt);
        detail.put("slaDueDate", slaDueDate);
        detail.put("resolutionSummary", null);
        detail.put("resolvedAt", c.getResolvedAt() != null ? c.getResolvedAt().toString() : null);
        // New closure/stage fields
        detail.put("closureClause", c.getClosureClause());
        detail.put("closedAt", c.getClosedAt() != null ? c.getClosedAt().toString() : null);

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

        // ═══ UST852: RE Activity Status — staff-only, and read-only ═══
        // This endpoint also serves the public tracker, so the entity's internal progress is gated on
        // the staff check above: telling a complainant their bank "has not opened" the record invites
        // a conversation RBI has not agreed to have. There is deliberately no setter anywhere on the
        // staff side — the ladder is derived from RE actions only.
        if (isStaff) {
            ReActivityStatus activity = c.getReActivityStatus() == null
                    ? ReActivityStatus.NOT_OPENED
                    : c.getReActivityStatus();

            Map<String, Object> reActivity = new LinkedHashMap<>();
            reActivity.put("status", activity.name());
            reActivity.put("labelKey", activity.translationKey());
            reActivity.put("changedAt", c.getReActivityChangedAt() != null
                    ? c.getReActivityChangedAt().toString() : null);
            reActivity.put("nudgeThresholdDays", c.getReActivityNudgeDays());
            reActivity.put("nudgedAt", c.getReActivityNudgedAt() != null
                    ? c.getReActivityNudgedAt().toString() : null);
            reActivity.put("readOnly", true);
            detail.put("reActivity", reActivity);

            // Ladder movements only, so staff can read the progression without wading through the
            // whole workflow timeline (UST852).
            detail.put("reActivityHistory", timeline.stream()
                    .filter(t -> t.getAction() != null && t.getAction().startsWith("RE_ACTIVITY_"))
                    .map(t -> {
                        Map<String, Object> h = new LinkedHashMap<>();
                        h.put("fromStatus", t.getFromStatus());
                        h.put("toStatus", t.getToStatus());
                        h.put("performedBy", t.getPerformedBy());
                        h.put("eventSource", t.getEventSource() != null ? t.getEventSource().name() : "MANUAL");
                        h.put("remarks", t.getRemarks());
                        h.put("timestamp", t.getPerformedAt() != null ? t.getPerformedAt().toString() : "");
                        return h;
                    })
                    .collect(Collectors.toList()));
        }
        detail.put("communications", List.of());
        detail.put("documents", List.of());
        detail.put("triageSignal", c.getTriageSignal());
        detail.put("triageFlags", c.getTriageFlags());
        detail.put("eligibilityTimeline", c.getEligibilityTimeline());

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

    private static final Set<String> WITHDRAWABLE_STATUSES = Set.of(
            "pending", "new", "assigned", "in_progress", "under_review", "escalated");

    @PostMapping("/{complaintNumber}/withdraw")
    public ResponseEntity<Map<String, Object>> withdrawComplaint(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> body,
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
                    "message", "Please select a reason for withdrawal.",
                    "timestamp", LocalDateTime.now().toString()));
        }
        if (reason.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Withdrawal reason must not exceed 500 characters.",
                    "timestamp", LocalDateTime.now().toString()));
        }

        // Validate: status allows withdrawal
        String currentStatus = complaint.getStatus() != null ? complaint.getStatus().toLowerCase() : "";
        if (!WITHDRAWABLE_STATUSES.contains(currentStatus)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "This complaint cannot be withdrawn in its current status (" + complaint.getStatus().toUpperCase() + ").",
                    "timestamp", LocalDateTime.now().toString()));
        }

        // Build combined reason with optional remarks
        String remarks = body.get("remarks") != null ? body.get("remarks").toString().trim() : "";
        String fullReason = remarks.isEmpty() ? reason : reason + " — " + remarks;

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

        // Add timeline entry
        complaintService.addTimeline(complaint.getId(), "withdrawn", complaint.getWithdrawnBy(),
                "Complaint withdrawn by complainant. Reason: " + fullReason,
                previousStatus, "withdrawn");

        // Publish Kafka event (if publisher is available)
        // eventPublisher pattern: use the existing publishComplaintClosed for status transitions
        // Since there's no dedicated withdrawn publisher, we skip Kafka here —
        // the outbox/event infrastructure can be added in a future phase.

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
            // UST875: this endpoint is anonymous, so it returned every recent complainant's real name
            // to any caller — a bulk harvest. Masked unconditionally; no caller here is identified.
            item.put("complainantName", piiMaskingService.apply("complainantName", c.getComplainantName(), false));
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

    /** Blank strings are normalised to null so an omitted optional field is not stored as "". */
    private static String asString(Object value) {
        if (value == null) return null;
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
