package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.GroundOfComplaintMaster;
import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.repository.ClosureClauseMasterRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.GroundOfComplaintMasterRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import com.hrms.cms.security.AaIdentityResolver;
import com.hrms.cms.security.AaRoleGuard;
import com.hrms.cms.service.AaAppealAutofillService;
import com.hrms.cms.service.AaAppealRegisterService;
import com.hrms.cms.service.AaParentComplaintSearchService;
import com.hrms.cms.service.AaParentComplaintSearchService.ParentSearchCriteria;
import com.hrms.cms.service.PiiMaskingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AA parent-complaint search, register-form autofill and appeal registration
 * (stories 1-15, 17, 18).
 *
 * Separate from AppealController (owned by S3) so the two do not collide, and because this controller's
 * subject is the PARENT COMPLAINT rather than the appeal.
 *
 * AUTHORISATION MODEL. Two distinct caller populations reach the same search:
 *
 *   - AA staff (AA_DO, AA_REVIEWER, AA_SECRETARIAT, AA_ADMIN) search nationally. Stories 1-4 require
 *     AA_DO and AA_REVIEWER to have identical fields and behaviour, so they share one handler rather
 *     than a copied one that could drift.
 *   - RE Principal Nodal Officers (RE_PNO, RE_NODAL_OFFICER) are restricted to their OWN entity and to
 *     appeal-eligible parents (story 5). Their scope is resolved from the token's entity_code claim.
 *
 * The scope is derived CLAIM-FIRST and is never taken from a request parameter or body. This is the
 * exact defect S1 closed elsewhere: the old RE path trusted X-Entity-Code ahead of the token, so any
 * authenticated RE user could read another bank's complaints by setting one header. Dev headers are
 * honoured only when cms.security.allow-dev-identity-headers is true.
 */
@RestController
@RequestMapping("/api/v1/aa/parent-complaints")
@RequiredArgsConstructor
@Slf4j
public class AaParentComplaintController {

    private static final List<String> AA_ROLES =
            List.of("AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN");
    private static final List<String> RE_ROLES = List.of("RE_PNO", "RE_NODAL_OFFICER");

    /** Scheme in force. Read from config, never hardcoded per the eligibility source-of-truth rule. */
    @Value("${cms.eligibility.scheme-version:RBIOS_2021}")
    private String schemeVersion;

    @Value("${cms.security.allow-dev-identity-headers:false}")
    private boolean allowDevIdentityHeaders;

    private final AaParentComplaintSearchService searchService;
    private final AaAppealAutofillService autofillService;
    private final AaAppealRegisterService registerService;
    private final AaIdentityResolver aaIdentityResolver;
    private final ComplaintRepository complaintRepository;
    private final GroundOfComplaintMasterRepository groundRepository;
    private final OfficeCodeMasterRepository officeRepository;
    private final ClosureClauseMasterRepository clauseRepository;
    private final PiiMaskingService piiMaskingService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Parent-complaint search (stories 1-5).
     *
     * All filters are optional and combine as AND. At least one is required: an unfiltered search over
     * the national complaint table is neither useful nor safe to serve.
     */
    @GetMapping("/search")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN",
                          "RE_PNO", "RE_NODAL_OFFICER", "ADMIN"})
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String complaintNumber,
            @RequestParam(required = false) String appellantName,
            @RequestParam(required = false) String appellantMobile,
            @RequestParam(required = false) String appellantEmail,
            @RequestParam(required = false) String rbioOfficeCode,
            @RequestParam(required = false) String closureClause,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long groundOfComplaintId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpRequest) {

        CallerScope caller = resolveCallerScope(httpRequest);

        ParentSearchCriteria criteria = ParentSearchCriteria.builder()
                .complaintNumber(complaintNumber)
                .appellantName(appellantName)
                .appellantMobile(appellantMobile)
                .appellantEmail(appellantEmail)
                .rbioOfficeCode(rbioOfficeCode)
                .closureClause(closureClause)
                .categoryId(categoryId)
                .groundOfComplaintId(groundOfComplaintId)
                .entityScope(caller.entityScope())
                // An RE/PNO caller only ever sees appeal-eligible parents (story 5). AA staff may
                // legitimately look up any parent, so eligibility is a flag on the row for them.
                .appealEligibleOnly(caller.entityScope() != null)
                .page(page)
                .size(size)
                .build();

        if (!searchService.hasAnyFilter(criteria)) {
            return buildResponse(HttpStatus.BAD_REQUEST, false,
                    "At least one search filter is required", "aa.search.error_no_filter", null);
        }
        if (searchService.hasTooShortPartialTerm(criteria)) {
            return buildResponse(HttpStatus.BAD_REQUEST, false,
                    "Search term is too short", "aa.search.error_term_too_short", null);
        }

        Page<Complaint> results = searchService.search(criteria);

        // PII is masked by default for every caller; an RE caller can never reveal (they belong to the
        // bank being complained about). Masking happens server-side so the full value never ships.
        List<Map<String, Object>> rows = results.getContent().stream()
                .map(c -> toSearchRow(c, caller))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("results", rows);
        data.put("page", results.getNumber());
        data.put("size", results.getSize());
        data.put("totalElements", results.getTotalElements());
        data.put("totalPages", results.getTotalPages());
        // Distinguishes "no matches" from "not searched yet" so the UI can show the right message.
        data.put("emptyMessageKey", rows.isEmpty() ? "aa.search.no_results" : null);
        return buildResponse(HttpStatus.OK, true, "Search completed", null, data);
    }

    /**
     * Register-form autofill for one parent (stories 8, 9, 10).
     *
     * Returns unresolvedFields so the UI leaves those inputs empty and explains why, rather than
     * showing a plausible guess. See AaAppealAutofillService for why each field has no source.
     */
    @GetMapping("/{complaintNumber}/register-form")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN",
                          "RE_PNO", "RE_NODAL_OFFICER", "ADMIN"})
    public ResponseEntity<Map<String, Object>> registerForm(@PathVariable String complaintNumber,
                                                            HttpServletRequest httpRequest) {
        CallerScope caller = resolveCallerScope(httpRequest);
        Complaint parent = loadScopedParent(complaintNumber, caller);

        // Story 6: the affordance appears only for a closed/reopened parent. Reported as data so the
        // client renders it, and re-enforced at registration because a hidden button is not a control.
        boolean eligible = searchService.isAppealEligible(parent);

        String mode = caller.entityScope() != null
                ? AaAppealAutofillService.MODE_RE_PNO
                : AaAppealAutofillService.MODE_AA_MANUAL;

        AaAppealAutofillService.AutofillResult autofill = autofillService.buildFrom(parent, mode);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", parent.getComplaintNumber());
        data.put("appealEligible", eligible);
        data.put("appealEligibilityKey", eligible ? null : "aa.register.error_parent_not_appealable");
        data.put("complainant", autofill.complainant());
        data.put("entity", autofill.entity());
        // Story 10: mode of receipt is derived from the intake channel and is read-only to the client.
        data.put("modeOfReceipt", autofill.modeOfReceipt());
        data.put("modeOfReceiptReadOnly", true);
        data.put("unresolvedFields", autofill.unresolvedFields().stream()
                .map(u -> Map.of("field", u.field(), "reasonKey", u.reasonKey()))
                .toList());
        data.put("closureClause", parent.getClosureClause());
        data.put("edApprovalRequired", caller.entityScope() != null);
        return buildResponse(HttpStatus.OK, true, "Register form prepared", null, data);
    }

    /**
     * Registers the appeal (stories 12, 13, 14, 15, 17, 18).
     *
     * Classification is derived from the parent's closure clause and the appealing party; any
     * client-supplied classification is ignored. An unmapped clause propagates as the 503 fail-closed
     * response from AppealClassificationService — deliberately not caught here.
     */
    @PostMapping("/{complaintNumber}/appeals")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_ADMIN",
                          "RE_PNO", "RE_NODAL_OFFICER", "ADMIN"})
    public ResponseEntity<Map<String, Object>> register(@PathVariable String complaintNumber,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpServletRequest httpRequest) {
        CallerScope caller = resolveCallerScope(httpRequest);

        String mode = caller.entityScope() != null
                ? AaAppealAutofillService.MODE_RE_PNO
                : AaAppealAutofillService.MODE_AA_MANUAL;

        AaAppealRegisterService.RegisterRequest request =
                AaAppealRegisterService.RegisterRequest.builder()
                        .complaintNumber(complaintNumber)
                        .appealFiledBy(str(body, "appealFiledBy"))
                        .sourceOfAppeal(str(body, "sourceOfAppeal"))
                        .appealGround(str(body, "appealGround"))
                        .reliefSought(str(body, "reliefSought"))
                        .reasonForDelay(str(body, "reasonForDelay"))
                        .appellantName(str(body, "appellantName"))
                        .appellantEmail(str(body, "appellantEmail"))
                        .appellantPhone(str(body, "appellantPhone"))
                        .appellantAddress1(str(body, "appellantAddress1"))
                        .appellantAddress2(str(body, "appellantAddress2"))
                        .appellantCity(str(body, "appellantCity"))
                        .appellantDistrict(str(body, "appellantDistrict"))
                        .appellantState(str(body, "appellantState"))
                        .appellantCountry(str(body, "appellantCountry"))
                        .appellantPincode(str(body, "appellantPincode"))
                        .categoryId(lng(body, "categoryId"))
                        .entityName(str(body, "entityName"))
                        .entityRegion(str(body, "entityRegion"))
                        .entityCategory(str(body, "entityCategory"))
                        .entityBranch(str(body, "entityBranch"))
                        .bsrIfscCode(str(body, "bsrIfscCode"))
                        .accountNumber(str(body, "accountNumber"))
                        .cardNumber(str(body, "cardNumber"))
                        .nodalOfficerName(str(body, "nodalOfficerName"))
                        .isComplainantAdvocate(bool(body, "isComplainantAdvocate"))
                        .hasRelatedCourtTrial(bool(body, "hasRelatedCourtTrial"))
                        .edApprovalGiven(bool(body, "edApprovalGiven"))
                        .edApprovalDate(str(body, "edApprovalDate"))
                        .edApprovalComments(str(body, "edApprovalComments"))
                        // Server-derived: the client cannot declare its own channel or entity.
                        .modeOfReceipt(mode)
                        .entityScope(caller.entityScope())
                        .actor(caller.actor())
                        .actorRole(caller.role())
                        .build();

        try {
            Map<String, Object> result = registerService.register(request);
            return buildResponse(HttpStatus.CREATED, true, "Appeal registered", null, result);
        } catch (AaAppealRegisterService.RegistrationValidationException e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("missingFields", e.getMissingFields());
            return buildResponse(HttpStatus.BAD_REQUEST, false, e.getMessage(),
                    "aa.register.error_mandatory_incomplete", data);
        } catch (AaAppealRegisterService.RegistrationDeniedException e) {
            return buildResponse(HttpStatus.FORBIDDEN, false, e.getMessage(),
                    "aa.register.error_not_permitted", null);
        }
    }

    /** Ground-of-complaint dropdown, sourced from GROUND_OF_COMPLAINT_MASTER — never a literal list. */
    @GetMapping("/masters/grounds")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN",
                          "RE_PNO", "RE_NODAL_OFFICER", "ADMIN"})
    public ResponseEntity<Map<String, Object>> grounds() {
        List<Map<String, Object>> data = groundRepository
                .findInForce(schemeVersion, LocalDate.now())
                .stream()
                .map(this::toGroundRow)
                .toList();
        return buildResponse(HttpStatus.OK, true, "Grounds retrieved", null, data);
    }

    /** RBIO office dropdown, sourced from OFFICE_CODE_MASTER. */
    @GetMapping("/masters/offices")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN",
                          "RE_PNO", "RE_NODAL_OFFICER", "ADMIN"})
    public ResponseEntity<Map<String, Object>> offices() {
        List<Map<String, Object>> data = officeRepository.findAll().stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsActive()))
                .sorted((a, b) -> a.getOfficeCode().compareTo(b.getOfficeCode()))
                .map(this::toOfficeRow)
                .toList();
        return buildResponse(HttpStatus.OK, true, "Offices retrieved", null, data);
    }

    /** Closure-clause dropdown, sourced from CLOSURE_CLAUSE_MASTER for the scheme in force. */
    @GetMapping("/masters/closure-clauses")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN",
                          "RE_PNO", "RE_NODAL_OFFICER", "ADMIN"})
    public ResponseEntity<Map<String, Object>> closureClauses() {
        List<Map<String, Object>> data = clauseRepository
                .findBySchemeVersionAndActiveTrueOrderByClauseCodeAsc(schemeVersion)
                .stream()
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("clauseCode", c.getClauseCode());
                    row.put("label", c.getLabel());
                    row.put("labelKey", c.getLabelKey());
                    row.put("appealableByComplainant", c.isAppealableByComplainant());
                    row.put("appealableByEntity", c.isAppealableByEntity());
                    return row;
                })
                .toList();
        return buildResponse(HttpStatus.OK, true, "Closure clauses retrieved", null, data);
    }

    private Map<String, Object> toGroundRow(GroundOfComplaintMaster g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId());
        row.put("groundCode", g.getGroundCode());
        row.put("label", g.getLabel());
        row.put("labelKey", g.getLabelKey());
        return row;
    }

    private Map<String, Object> toOfficeRow(OfficeCodeMaster o) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("officeCode", o.getOfficeCode());
        row.put("officeName", o.getOfficeName());
        return row;
    }

    /**
     * Loads a parent, refusing one outside an entity-scoped caller's entity.
     *
     * 404 vs 403: an entity-scoped caller asking about someone else's complaint is told the complaint
     * was not found, not that it exists but is forbidden. A 403 confirms the complaint number is real,
     * which turns this endpoint into an oracle for probing other banks' complaint numbers.
     */
    private Complaint loadScopedParent(String complaintNumber, CallerScope caller) {
        Complaint parent = complaintRepository.findByComplaintNumber(complaintNumber.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No complaint found with number " + complaintNumber));

        if (caller.entityScope() != null) {
            String scope = caller.entityScope().trim();
            String parentEntity = parent.getEntityCode() == null ? "" : parent.getEntityCode().trim();
            if (scope.isEmpty() || !scope.equalsIgnoreCase(parentEntity)) {
                log.warn("Entity-scoped caller (scope='{}') denied access to parent {} of entity '{}'",
                        scope, parent.getComplaintNumber(), parentEntity);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No complaint found with number " + complaintNumber);
            }
        }
        return parent;
    }

    private Map<String, Object> toSearchRow(Complaint c, CallerScope caller) {
        boolean revealed = false; // Search results are always masked; reveal is an explicit action.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("complaintNumber", c.getComplaintNumber());
        row.put("complainantName", piiMaskingService.apply("complainantName", c.getComplainantName(), revealed));
        row.put("complainantEmail", piiMaskingService.apply("complainantEmail", c.getComplainantEmail(), revealed));
        row.put("complainantPhone", piiMaskingService.apply("complainantPhone", c.getComplainantPhone(), revealed));
        row.put("accountNumber", piiMaskingService.apply("accountNumber", c.getAccountNumber(), revealed));
        row.put("subject", c.getSubject());
        row.put("status", c.getStatus());
        row.put("workflowStage", c.getWorkflowStage());
        row.put("closureClause", c.getClosureClause());
        row.put("rbioOfficeCode", c.getRbioOfficeCode());
        row.put("categoryId", c.getCategoryId());
        row.put("groundOfComplaintId", c.getGroundOfComplaintId());
        row.put("entityCode", c.getEntityCode());
        row.put("closedAt", c.getClosedAt() != null ? c.getClosedAt().toString() : null);
        row.put("reopenedAt", c.getReopenedAt() != null ? c.getReopenedAt().toString() : null);
        row.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        // Story 6: drives the 'Create Appeal/Representation' affordance on the detail page.
        row.put("appealEligible", searchService.isAppealEligible(c));
        row.put("piiMasked", piiMaskingService.isMaskingEnabled());
        return row;
    }

    /** The caller's AA role, acting id, and (for RE callers) their entity scope. */
    private record CallerScope(String actor, String role, String entityScope) {}

    /**
     * Resolves who is calling and what they may see.
     *
     * An RE role yields a mandatory entity scope; an AA role yields none. A caller holding an RE role
     * whose entity cannot be established is refused outright rather than defaulted — the old code
     * returned a literal "UNKNOWN_ENTITY" sentinel, which silently turned a failed resolution into a
     * real-looking scope.
     */
    private CallerScope resolveCallerScope(HttpServletRequest request) {
        java.util.Set<String> roles = aaIdentityResolver.resolveRoles();
        String actor = aaIdentityResolver.resolveActor();

        boolean isRe = roles.stream().anyMatch(RE_ROLES::contains);
        boolean isAa = roles.stream().anyMatch(AA_ROLES::contains) || roles.contains("ADMIN");

        // An AA role wins when a caller somehow holds both: AA staff are not entity-scoped, and an
        // AA_ADMIN who also carried an RE role must not be silently narrowed to one bank.
        if (isAa) {
            return new CallerScope(actor, aaIdentityResolver.resolveAaRole(), null);
        }

        if (isRe) {
            String entityCode = resolveEntityCode(request);
            if (entityCode == null || entityCode.isBlank()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Your regulated entity could not be determined from your session. Please sign in again.");
            }
            String role = roles.contains("RE_PNO") ? "RE_PNO" : "RE_NODAL_OFFICER";
            return new CallerScope(actor, role, entityCode);
        }

        // The role guard admits only the roles above, so this is unreachable in practice; refusing
        // rather than returning an unscoped view keeps that assumption from becoming a hole.
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    /**
     * Entity code, CLAIM-FIRST.
     *
     * The token wins. X-Entity-Code / X-User-Entity are honoured only under dev-local, where the E2E
     * suites drive the API with them. This mirrors RePortalController.extractEntityCode; it is
     * duplicated rather than shared because that method is private on a controller this session does
     * not own, and reaching into it would mean editing another owner's file.
     */
    private String resolveEntityCode(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String[] parts = authHeader.substring(7).split("\\.");
                if (parts.length >= 2) {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
                    Object code = claims.get("entity_code");
                    if (code != null && !code.toString().isBlank()) {
                        return code.toString().trim();
                    }
                }
            } catch (Exception e) {
                log.debug("Could not decode entity_code claim: {}", e.getMessage());
            }
        }

        if (allowDevIdentityHeaders) {
            return Optional.ofNullable(request.getHeader("X-Entity-Code"))
                    .filter(h -> !h.isBlank())
                    .or(() -> Optional.ofNullable(request.getHeader("X-User-Entity")).filter(h -> !h.isBlank()))
                    .map(String::trim)
                    .orElse(null);
        }
        return null;
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    private static Long lng(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = value.toString().trim();
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Tri-state boolean: null when absent, so "not answered" stays distinguishable from "No".
     * The mandatory-declaration checks in the register service depend on that distinction.
     */
    private static Boolean bool(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("yes")) {
            return Boolean.TRUE;
        }
        if (s.equalsIgnoreCase("false") || s.equals("0") || s.equalsIgnoreCase("no")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, boolean success,
                                                              String message, String messageKey,
                                                              Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        if (messageKey != null) {
            response.put("messageKey", messageKey);
        }
        response.put("data", data);
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(response);
    }
}
