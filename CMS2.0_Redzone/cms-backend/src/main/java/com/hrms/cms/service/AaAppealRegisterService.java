package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Register-milestone intake: creates an Appeal from a closed/reopened parent complaint
 * (stories 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 18).
 *
 * This is a NEW service rather than a change to AppealWorkflowService.fileAppeal, which S3 owns and
 * which serves the citizen quick-file path (it hardcodes appellantName="Citizen" and sets none of the
 * register-milestone fields). Both funnel through the same two invariants below.
 *
 * TWO SERVER-SIDE INVARIANTS, both enforced here and not merely in the UI:
 *
 *   1. PARENT ELIGIBILITY. Only a closed or reopened complaint can be appealed. Story 6 hides the
 *      button on an open complaint; hiding a button is not a control, so intake re-checks. Reopen is
 *      workflow_stage='REOPENED', not a status, so both halves are tested.
 *
 *   2. CLASSIFICATION IS DERIVED. AppealClassificationService.classify(parent, party) decides
 *      APPEAL vs REPRESENTATION from the parent's closure clause and the appealing PARTY. The client's
 *      value is ignored, and an unmapped clause fails closed as a 503 from the classification service
 *      (never a 400 or 500) so no citizen is told they have no recourse because of a configuration gap.
 *
 * ENTITY SCOPE (story 14): when the caller is an RE/PNO, entityScope is the entity resolved from their
 * token by the controller. A parent belonging to another entity is refused. The scope is never read
 * from the request body.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaAppealRegisterService {

    /** Mandatory register-milestone fields (story 7). */
    private static final List<String> MANDATORY_FIELDS =
            List.of("appealFiledBy", "sourceOfAppeal", "appealGround");

    /** Mandatory complainant fields validated before save (story 8). */
    private static final List<String> MANDATORY_COMPLAINANT_FIELDS =
            List.of("appellantName", "appellantPhone", "appellantAddress1", "appellantCity",
                    "appellantState", "categoryId");

    private final ComplaintRepository complaintRepository;
    private final AppealRepository appealRepository;
    private final AppealClassificationService classificationService;
    private final AaParentComplaintSearchService parentSearchService;
    private final AaAppealAssignmentPort assignmentPort;
    private final NotificationService notificationService;

    /** Thrown when the caller may not register an appeal against this parent. */
    public static class RegistrationDeniedException extends RuntimeException {
        public RegistrationDeniedException(String message) {
            super(message);
        }
    }

    /** Thrown when mandatory register-milestone data is missing or invalid. */
    @Getter
    public static class RegistrationValidationException extends RuntimeException {
        private final List<String> missingFields;

        public RegistrationValidationException(String message, List<String> missingFields) {
            super(message);
            this.missingFields = missingFields;
        }
    }

    @Getter
    @Builder
    public static class RegisterRequest {
        private final String complaintNumber;

        // Register milestone (story 7)
        private final String appealFiledBy;
        private final String sourceOfAppeal;
        private final String appealGround;
        private final String reliefSought;
        private final String reasonForDelay;

        // Complainant block (story 8)
        private final String appellantName;
        private final String appellantEmail;
        private final String appellantPhone;
        private final String appellantAddress1;
        private final String appellantAddress2;
        private final String appellantCity;
        private final String appellantDistrict;
        private final String appellantState;
        private final String appellantCountry;
        private final String appellantPincode;
        private final Long categoryId;

        // Entity block (story 9)
        private final String entityName;
        private final String entityRegion;
        private final String entityCategory;
        private final String entityBranch;
        private final String bsrIfscCode;
        private final String accountNumber;
        private final String cardNumber;
        private final String nodalOfficerName;

        // Declarations (story 11) — Boolean, not boolean: absent must be distinguishable from "No".
        private final Boolean isComplainantAdvocate;
        private final Boolean hasRelatedCourtTrial;

        // ED approval (story 15) — PNO channel only.
        private final Boolean edApprovalGiven;
        private final String edApprovalDate;
        private final String edApprovalComments;

        /** Set by the controller from the intake channel (story 10), never from the request. */
        private final String modeOfReceipt;

        /** Resolved from the caller's token. Null for AA staff; the entity code for an RE/PNO. */
        private final String entityScope;

        /** Resolved from the caller's token. */
        private final String actor;
        private final String actorRole;
    }

    @Transactional
    public Map<String, Object> register(RegisterRequest request) {
        if (request.getComplaintNumber() == null || request.getComplaintNumber().isBlank()) {
            throw new RegistrationValidationException("complaintNumber is required",
                    List.of("complaintNumber"));
        }

        Complaint parent = complaintRepository
                .findByComplaintNumber(request.getComplaintNumber().trim())
                .orElseThrow(() -> new RegistrationDeniedException(
                        "No complaint found with number " + request.getComplaintNumber()));

        // Invariant 1 — parent must be appealable at all.
        if (!parentSearchService.isAppealEligible(parent)) {
            throw new RegistrationDeniedException(
                    "Only a closed or reopened complaint can be appealed");
        }

        // Entity scope (story 14). Compared case/whitespace-insensitively because entity_code holds
        // both names and short codes; a scope that cannot be matched refuses rather than widens.
        if (request.getEntityScope() != null) {
            String scope = request.getEntityScope().trim();
            String parentEntity = parent.getEntityCode() == null ? "" : parent.getEntityCode().trim();
            if (scope.isEmpty() || !scope.equalsIgnoreCase(parentEntity)) {
                log.warn("Entity-scoped caller (scope='{}') refused registration against parent {} of entity '{}'",
                        scope, parent.getComplaintNumber(), parentEntity);
                throw new RegistrationDeniedException(
                        "This complaint does not belong to your regulated entity");
            }
        }

        validateMandatory(request);

        // One live appeal per parent. Without this a double-submit creates two appeals against the same
        // closure, and the second would be adjudicated on a record the first already superseded.
        Optional<Appeal> existing = appealRepository
                .findByOriginalComplaintNumber(parent.getComplaintNumber())
                .stream()
                .filter(a -> !isTerminal(a.getStatus()))
                .findFirst();
        if (existing.isPresent()) {
            throw new RegistrationDeniedException(
                    "An appeal is already open against complaint " + parent.getComplaintNumber()
                            + " (" + existing.get().getAppealNumber() + ")");
        }

        // Invariant 2 — classification derived server-side from clause + party.
        ClosureClauseMaster.AppealParty party = resolveParty(request);
        String classificationType = classificationService.classify(parent, party);

        String appealNumber = generateAppealNumber(parent);

        Appeal appeal = Appeal.builder()
                .appealNumber(appealNumber)
                .originalComplaintNumber(parent.getComplaintNumber())
                .classificationType(classificationType)
                .appealGround(trimToNull(request.getAppealGround()))
                .reliefSought(trimToNull(request.getReliefSought()))
                .reasonForDelay(trimToNull(request.getReasonForDelay()))
                .appellantName(trimToNull(request.getAppellantName()))
                .appellantEmail(trimToNull(request.getAppellantEmail()))
                .appellantPhone(trimToNull(request.getAppellantPhone()))
                .status("filed")
                .workflowStage("FILED")
                .priority("high")
                .assignedRole("AA_DO")
                // The PARTY, not the client's string. appealFiledBy decides legal standing — a
                // complainant may appeal 15(1)(a) and 15(1)(b), an entity only 15(1)(b) — so accepting
                // it from the body would let a caller declare its own standing and would contradict the
                // classification actually derived below. The field is still mandatory on the form
                // (the officer must answer it) but the stored value is authoritative.
                .appealFiledBy(party.name())
                .sourceOfAppeal(trimToNull(request.getSourceOfAppeal()))
                .modeOfReceipt(trimToNull(request.getModeOfReceipt()))
                .appellantAddress1(trimToNull(request.getAppellantAddress1()))
                .appellantAddress2(trimToNull(request.getAppellantAddress2()))
                .appellantCity(trimToNull(request.getAppellantCity()))
                .appellantDistrict(trimToNull(request.getAppellantDistrict()))
                .appellantState(trimToNull(request.getAppellantState()))
                .appellantCountry(trimToNull(request.getAppellantCountry()))
                .appellantPincode(trimToNull(request.getAppellantPincode()))
                .categoryId(request.getCategoryId())
                .entityCode(parent.getEntityCode())
                .entityName(trimToNull(request.getEntityName()))
                .entityRegion(trimToNull(request.getEntityRegion()))
                .entityCategory(trimToNull(request.getEntityCategory()))
                .entityBranch(trimToNull(request.getEntityBranch()))
                .bsrIfscCode(trimToNull(request.getBsrIfscCode()))
                .accountNumber(trimToNull(request.getAccountNumber()))
                .cardNumber(trimToNull(request.getCardNumber()))
                .nodalOfficerName(trimToNull(request.getNodalOfficerName()))
                .isComplainantAdvocate(request.getIsComplainantAdvocate())
                .hasRelatedCourtTrial(request.getHasRelatedCourtTrial())
                .edApprovalGiven(request.getEdApprovalGiven())
                .edApprovalDate(parseDateTime(request.getEdApprovalDate()))
                .edApprovalComments(trimToNull(request.getEdApprovalComments()))
                .closureClause(parent.getClosureClause())
                .createdBy(request.getActor())
                .createdByRole(request.getActorRole())
                .build();

        Appeal saved = appealRepository.save(appeal);

        // Assignment (stories 17, 19). Best-effort by contract: an unassigned appeal can be picked up,
        // whereas failing registration because assignment was down loses a statutory filing.
        Optional<String> officer = assignmentPort.assignDealingOfficer(saved.getAppealNumber());
        officer.ifPresent(saved::setAssignedOfficer);
        if (officer.isPresent()) {
            appealRepository.save(saved);
            notifyAssignedOfficer(saved, officer.get());
        } else {
            log.warn("Appeal {} registered without an assigned AA_DO — no officer pool available",
                    saved.getAppealNumber());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appealNumber", saved.getAppealNumber());
        result.put("originalComplaintNumber", saved.getOriginalComplaintNumber());
        result.put("classificationType", saved.getClassificationType());
        result.put("status", saved.getStatus());
        result.put("modeOfReceipt", saved.getModeOfReceipt());
        result.put("assignedRole", saved.getAssignedRole());
        result.put("assignedOfficer", saved.getAssignedOfficer());
        result.put("closureClause", saved.getClosureClause());
        // Story 11: a court trial surfaces a prompt to log the case in the Legal Cases module. There is
        // NO Legal Cases backend in this product, so the flag is persisted and the prompt is a key —
        // nothing is written to a module that does not exist.
        result.put("promptLegalCaseEntry", Boolean.TRUE.equals(saved.getHasRelatedCourtTrial()));
        if (Boolean.TRUE.equals(saved.getHasRelatedCourtTrial())) {
            result.put("promptLegalCaseEntryKey", "aa.register.prompt_log_legal_case");
        }
        return result;
    }

    /**
     * Validates the mandatory register-milestone and complainant fields (stories 7, 8, 11).
     *
     * Returns EVERY missing field rather than the first, so the officer completes the form in one pass
     * instead of rediscovering one blank at a time.
     */
    private void validateMandatory(RegisterRequest r) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("appealFiledBy", r.getAppealFiledBy());
        values.put("sourceOfAppeal", r.getSourceOfAppeal());
        values.put("appealGround", r.getAppealGround());
        values.put("appellantName", r.getAppellantName());
        values.put("appellantPhone", r.getAppellantPhone());
        values.put("appellantAddress1", r.getAppellantAddress1());
        values.put("appellantCity", r.getAppellantCity());
        values.put("appellantState", r.getAppellantState());
        values.put("categoryId", r.getCategoryId());

        List<String> missing = new ArrayList<>();
        for (String field : MANDATORY_FIELDS) {
            if (isBlankValue(values.get(field))) {
                missing.add(field);
            }
        }
        for (String field : MANDATORY_COMPLAINANT_FIELDS) {
            if (isBlankValue(values.get(field))) {
                missing.add(field);
            }
        }

        // Story 11: both declarations are mandatory. Boolean.FALSE is a valid answer; null is not.
        if (r.getIsComplainantAdvocate() == null) {
            missing.add("isComplainantAdvocate");
        }
        if (r.getHasRelatedCourtTrial() == null) {
            missing.add("hasRelatedCourtTrial");
        }

        // Story 15: for the PNO channel the ED approval answer itself is mandatory.
        if (MODE_RE_PNO.equals(r.getModeOfReceipt()) && r.getEdApprovalGiven() == null) {
            missing.add("edApprovalGiven");
        }

        if (!missing.isEmpty()) {
            throw new RegistrationValidationException(
                    "Mandatory fields are incomplete: " + String.join(", ", missing), missing);
        }
    }

    private static final String MODE_RE_PNO = AaAppealAutofillService.MODE_RE_PNO;

    /**
     * The appealing party, which decides appealability alongside the clause.
     *
     * Derived from the caller's own role/scope, not from a request field: whether the appellant is the
     * complainant or the regulated entity determines which clauses are appealable at all
     * (a complainant may appeal 15(1)(a) and 15(1)(b); an entity only 15(1)(b)), so a client-supplied
     * party would let the caller choose their own legal standing.
     */
    private ClosureClauseMaster.AppealParty resolveParty(RegisterRequest r) {
        if (r.getEntityScope() != null && !r.getEntityScope().isBlank()) {
            return ClosureClauseMaster.AppealParty.ENTITY;
        }
        return ClosureClauseMaster.AppealParty.COMPLAINANT;
    }

    /**
     * Notifies the assigned officer (story 19).
     *
     * Asserts nothing about real-time delivery: notification.service.ts opens a raw WebSocket against a
     * SockJS/STOMP endpoint and never subscribes, and the bell never calls it, so the push path is dead
     * (S2C owns that fix). The persisted IN_APP_NOTIFICATIONS row is the actual deliverable.
     */
    private void notifyAssignedOfficer(Appeal appeal, String officerId) {
        try {
            notificationService.send(
                    officerId,
                    "ASSIGNMENT",
                    "New appeal assigned",
                    "Appeal " + appeal.getAppealNumber() + " has been assigned to you for registration review.",
                    appeal.getAppealNumber(),
                    "APPEAL",
                    "/aa/appeal/" + appeal.getAppealNumber());
        } catch (Exception e) {
            log.warn("Could not notify officer {} about appeal {}: {}",
                    officerId, appeal.getAppealNumber(), e.getMessage());
        }
    }

    /**
     * Appeal number: A{parent FY+office}{sequence}, derived from the parent so an appeal is traceable to
     * the office that closed the complaint. Falls back to a timestamp suffix for legacy parents whose
     * number carries no office (the 'CMP-'/'CMS-DEMO-' formats).
     */
    private String generateAppealNumber(Complaint parent) {
        String base = parent.getRbioOfficeCode() != null
                ? parent.getRbioOfficeCode()
                : "000";
        long sequence = appealRepository.count() + 1;
        String candidate = String.format("A%s%s%06d", yearPart(), base, sequence);
        while (appealRepository.findByAppealNumber(candidate).isPresent()) {
            sequence++;
            candidate = String.format("A%s%s%06d", yearPart(), base, sequence);
        }
        return candidate;
    }

    private String yearPart() {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        return now.getMonthValue() >= 4
                ? String.valueOf(year) + String.format("%02d", (year + 1) % 100)
                : String.valueOf(year - 1) + String.format("%02d", year % 100);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException e) {
            try {
                return java.time.LocalDate.parse(trimmed).atStartOfDay();
            } catch (DateTimeParseException inner) {
                throw new RegistrationValidationException(
                        "edApprovalDate must be an ISO date or date-time", List.of("edApprovalDate"));
            }
        }
    }

    private boolean isTerminal(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim().toLowerCase();
        return s.equals("closed") || s.equals("rejected") || s.equals("order_passed");
    }

    private static boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof String s && s.isBlank();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
