package com.hrms.cms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.CepcComplaintAssessment;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintEligibilityAnswer;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.CepcComplaintAssessmentRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintEligibilityAnswerRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The CEPC complaint detail view's Summary tab: the nested payload behind
 * {@code GET/PUT /api/complaints/cepc/{id}/summary}.
 *
 * <p>This is the call that gates the whole screen. The component blocks on it, keys every other tab off the
 * complaint number it returns, and forces itself read-only when it fails — so a complaint whose summary
 * does not load renders six empty tabs no matter how healthy their own endpoints are.
 *
 * <p>The payload spans three rows: {@link Complaint} for what the complainant filed,
 * {@link CepcComplaintAssessment} for what the officer assessed, and
 * {@link ComplaintEligibilityAnswer} for the maintainability panel. Which row a field lands on is not a
 * matter of taste — see {@link CepcComplaintAssessment} for why the assessment cannot live on the parent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CepcComplaintSummaryService {

    /**
     * The maintainability panel's questions, in the vocabulary the client uses on the wire.
     *
     * <p>These are the officer panel's keys, which are NOT the ones {@code EligibilityQuestionMaster}
     * holds — that master carries the citizen intake wizard's 14 questions under different names. See
     * {@link ComplaintEligibilityAnswer} for why this is not resolved against it.
     */
    private static final List<String> ELIGIBILITY_KEYS = List.of(
            "entityRegulatedByRbi",
            "complaintNotDirectlyAddressedToOmbudsman",
            "complaintNotRegisteredWithEntity",
            "frivolousVexatiousThreatening",
            "subJudiceOrArbitration",
            "sameGrievancePendingBeforeCourt",
            "sameGrievanceSettledBeforeCourt",
            "complaintMadeThroughAdvocate",
            "complainantIsAdvocate",
            "sameGrievancePendingBeforeOmbudsman",
            "alreadyDealtWithByOmbudsman",
            "complaintAgainstManagement",
            "complaintFiledWithCEPCOrRBI",
            "disputeBetweenREs",
            "staffOfREEmployerRelationship",
            "completeInformationUnavailable",
            "writtenComplaintFiledWithRE",
            "firstFiledWithREDate",
            "receivedReplyFromEntity",
            "replyDate");

    /** The two questions answered with a date rather than yes/no. */
    private static final Set<String> ELIGIBILITY_DATE_KEYS = Set.of("firstFiledWithREDate", "replyDate");

    /**
     * How the client spells {@code WEB_PORTAL}.
     *
     * <p>Translated at this boundary rather than anywhere else. The Mode of Receipt dropdown offers
     * {@code PORTAL}, and a portal filing is stored as {@code WEB_PORTAL} — so without the mapping the
     * field renders blank for the commonest kind of complaint there is, and reads as missing data.
     */
    private static final String STORED_PORTAL = "WEB_PORTAL";
    private static final String CLIENT_PORTAL = "PORTAL";

    private static final DateTimeFormatter DAY_FIRST = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** The Forward tab's draft fields, which travel together as one JSON column. */
    private static final List<String> FORWARD_DRAFT_KEYS = List.of(
            "target",
            "regulatorName",
            "regulatorEmail",
            "departmentName",
            "departmentEmail",
            "officeCode");

    private final ComplaintRepository complaintRepository;
    private final CepcComplaintAssessmentRepository assessmentRepository;
    private final ComplaintEligibilityAnswerRepository eligibilityRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final BankRepository bankRepository;
    private final ObjectMapper objectMapper;

    /** Thrown when {@code id} addresses no complaint, so the controller can answer 404 rather than 500. */
    public static class ComplaintNotFoundException extends RuntimeException {
        public ComplaintNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when the caller may read the summary but not change it. */
    public static class NotEditableException extends RuntimeException {
        public NotEditableException(String message) {
            super(message);
        }
    }

    /** Thrown when a patched field breaks an app-level constraint the column itself doesn't enforce. */
    public static class InvalidFieldException extends RuntimeException {
        public InvalidFieldException(String message) {
            super(message);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> read(String idOrNumber, String callerUserId, boolean admin) {
        return toSummary(resolve(idOrNumber), callerUserId, admin);
    }

    /**
     * Applies a partial summary and returns the re-read result.
     *
     * <p><b>Presence of a key, not its value, decides whether a field is touched.</b> Two different buttons
     * on this screen PUT here: Update Complaint sends the whole form, while Save Assessment sends only
     * {@code complainDetailsDto.additionalInformation} with three of that block's five fields. If an absent
     * key were read as null, saving the assessment would blank the officer's comments and the vernacular
     * language, and saving the form would blank the proposed clause and the speaking order. A key that IS
     * present and null is an intentional clear — the client nulls the vernacular language when the toggle
     * is off — so nulls must still be applied.
     *
     * <p>This is why the patch arrives as a map. Binding it to a DTO would collapse "absent" and "null"
     * into the same thing before this method could tell them apart.
     */
    @Transactional
    public Map<String, Object> update(String idOrNumber, Map<String, Object> patch,
                                      String callerUserId, boolean admin) {
        Complaint complaint = resolve(idOrNumber);
        if (!canEdit(complaint, callerUserId, admin)) {
            throw new NotEditableException("This complaint is not open for you to edit.");
        }
        if (patch == null || patch.isEmpty()) {
            return toSummary(complaint, callerUserId, admin);
        }

        CepcComplaintAssessment assessment = assessmentRepository
                .findByComplaintNumber(complaint.getComplaintNumber())
                .orElseGet(() -> CepcComplaintAssessment.builder()
                        .complaintNumber(complaint.getComplaintNumber())
                        .build());

        applyBasicDetails(sub(patch, "basicDetailsDto"), complaint, assessment);
        applyEntityDetails(sub(patch, "entityDetails"), assessment);
        applyComplainDetails(sub(patch, "complainDetailsDto"), assessment);
        applyEligibility(sub(patch, "eligibility"), complaint.getComplaintNumber(), assessment);
        applyFinalDecision(sub(patch, "finalDecisionDto"), assessment);
        applyForwardDraft(sub(patch, "forwardDraftDto"), assessment);

        assessment.setLastModifiedBy(callerUserId);
        assessmentRepository.save(assessment);
        complaintRepository.save(complaint);

        return toSummary(complaint, callerUserId, admin);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Write
    // ════════════════════════════════════════════════════════════════════════

    private void applyBasicDetails(Map<String, Object> block, Complaint complaint,
                                   CepcComplaintAssessment assessment) {
        if (block == null) {
            return;
        }
        strRequired(block, "subject", 255, complaint::setSubject);
        strRequiredRange(block, "complainDetails", 20, 2000, complaint::setDescription);
        str(block, "complainantName", complaint::setComplainantName);
        str(block, "emailId", complaint::setComplainantEmail);
        str(block, "mobile", complaint::setComplainantPhone);
        str(block, "modeOfReceipt", v -> complaint.setFilingType(toStoredFilingType(v)));
        day(block, "receiptDate", v -> applyReceiptDate(complaint, v));

        str(block, "comments", assessment::setOfficerComments);
        flag(block, "complaintCpgram", assessment::setComplaintCpgram);
        str(block, "cpgramNumber", assessment::setCpgramNumber);
    }

    /**
     * Moves {@code filedAt} only when the calendar day actually changes.
     *
     * <p>The client round-trips this field as a bare date, so writing it back unconditionally would drop
     * the time of day on every save — every complaint's filing time would drift to midnight and the
     * dashboard's date ordering would start tying within a day.
     */
    private void applyReceiptDate(Complaint complaint, LocalDate day) {
        if (day == null) {
            complaint.setFiledAt(null);
            return;
        }
        LocalDateTime current = complaint.getFiledAt();
        if (current != null && current.toLocalDate().equals(day)) {
            return;
        }
        complaint.setFiledAt(day.atStartOfDay());
    }

    private void applyEntityDetails(Map<String, Object> block, CepcComplaintAssessment assessment) {
        if (block == null) {
            return;
        }
        // Every column here is a complaint-scoped override, never a write to the shared master row: the
        // officer correcting this complaint's branch must not retitle the entity for every other complaint
        // filed against it.
        if (block.containsKey("id")) {
            assessment.setRegulatedEntityId(longValue(block.get("id")));
        }
        str(block, "entityName", assessment::setEntityName);
        str(block, "moduleName", assessment::setModuleName);
        str(block, "entityCategory", assessment::setEntityCategory);
        str(block, "bsrCode", assessment::setBsrCode);
        str(block, "pincode", assessment::setEntityPincode);
        str(block, "country", assessment::setEntityCountry);
        str(block, "state", assessment::setEntityState);
        str(block, "district", assessment::setEntityDistrict);
        str(block, "city", assessment::setEntityCity);
        str(block, "branchName", assessment::setEntityBranchName);
        str(block, "branchCategory", assessment::setEntityBranchCategory);
        str(block, "branchCenterName", assessment::setBranchCenterName);
        str(block, "entityAddress", assessment::setEntityAddress);
    }

    private void applyComplainDetails(Map<String, Object> block, CepcComplaintAssessment a) {
        if (block == null) {
            return;
        }

        Map<String, Object> identification = sub(block, "basicIdentificationDto");
        if (identification != null) {
            str(identification, "otherEntityName", a::setOtherEntityName);
            day(identification, "registrationWithRbiDate", a::setRegistrationWithRbiDate);
        }

        Map<String, Object> classification = sub(block, "complaintClassification");
        if (classification != null) {
            str(classification, "complaintCategory", a::setComplaintCategoryText);
            str(classification, "complaintSubCategory1", a::setComplaintSubCategory1);
            str(classification, "complaintSubCategory2", a::setComplaintSubCategory2);
            flag(classification, "complaintRegistrationDateValid", a::setComplaintRegistrationDateValid);
            day(classification, "dateOfFilingComplaint", a::setDateOfFilingComplaint);
        }

        Map<String, Object> financial = sub(block, "financialDetails");
        if (financial != null) {
            flag(financial, "reminderSent", a::setReminderSent);
            amount(financial, "disputedAmount", a::setDisputedAmount);
            num(financial, "compensationSought", a::setCompensationSought);
        }

        Map<String, Object> legal = sub(block, "legalCaseDetails");
        if (legal != null) {
            flag(legal, "legalCaseFiled", a::setLegalCaseFiled);
            flag(legal, "preEnquiryReceived", a::setPreEnquiryReceived);
            flag(legal, "highPriorityComplaint", a::setHighPriorityComplaint);
            amount(legal, "loanDisposalAmount", a::setLoanDisposalAmount);
        }

        Map<String, Object> additional = sub(block, "additionalInformation");
        if (additional != null) {
            str(additional, "comments", a::setAdditionalComments);
            str(additional, "crpcProposedAction", a::setCrpcProposedAction);
            str(additional, "proposedClause", a::setProposedClause);
            str(additional, "speakingOrderContent", a::setSpeakingOrderContent);
            str(additional, "vernacularLanguage", a::setVernacularLanguage);
        }

        Map<String, Object> flags = sub(block, "flagsAndIndicators");
        if (flags != null) {
            flag(flags, "complaintRegardingPension", a::setComplaintRegardingPension);
            flag(flags, "complaintAgainstBusinessCorrespondent",
                    a::setComplaintAgainstBusinessCorrespondent);
            flag(flags, "atmCreditDebitCard", a::setAtmCreditDebitCard);
            str(flags, "schemeFlag", a::setSchemeFlag);
            str(flags, "rboCgpcOld", a::setRboCgpcOld);
            str(flags, "groundsFlag", a::setGroundsFlag);
        }

        Map<String, Object> linkage = sub(block, "complaintLinkage");
        if (linkage != null) {
            flag(linkage, "freeMarkedComplaint", a::setFreeMarkedComplaint);
            str(linkage, "replyWithin30Days", a::setReplyWithin30Days);
            // currentComplaintNumber is read-only: it is this complaint's own number, echoed for display.
        }
    }

    /**
     * The Final Decision tab's draft, saved before anything is dispatched.
     *
     * <p>Distinct from {@code CepcSendForApprovalController.applyFinalDecisionNarrative}, which writes most
     * of the same columns but only AFTER the workflow transition has succeeded and the complaint is closed.
     * This is the same officer's work in progress, so it must move no status and fire no transition.
     *
     * <p>Six of these reuse columns the closure path already writes. That overlap is deliberate: a draft
     * that lands in different columns from the committed decision would have to be reconciled at closure,
     * and the closure path overwrites them anyway with whatever the officer finally confirmed.
     */
    private void applyFinalDecision(Map<String, Object> block, CepcComplaintAssessment a) {
        if (block == null) {
            return;
        }
        str(block, "action", a::setFinalDecisionAction);

        // Not Complaint.closureClause — see the field's javadoc. A draft must not overwrite a clause that
        // closure already committed.
        str(block, "closureClause", a::setClosureClauseDraft);
        str(block, "closureClauseDescription", a::setClosureClauseDescription);
        str(block, "complaintStatusOnPortal", a::setComplaintStatusOnPortal);
        strMax(block, "gistOfCase", MAX_GIST_LENGTH, a::setGistOfCase);
        strMax(block, "gistOfCaseRegional", MAX_GIST_LENGTH, a::setGistOfCaseRegional);
        str(block, "rejectWithdrawSettleSubAction", a::setRejectWithdrawSettleSubAction);
        str(block, "rejectWithdrawSettleReason", a::setRejectWithdrawSettleReason);

        flag(block, "speakingOrderGenerated", a::setSpeakingOrderGenerated);

        day(block, "advisoryComplianceDate", a::setAdvisoryComplianceDate);
        day(block, "awardImplementationDate", a::setAwardImplementationDate);
        day(block, "awardAcceptanceDate", a::setAwardAcceptanceDate);

        amount(block, "disputedAmount", a::setDisputedAmount);
        amount(block, "compensationLoss", a::setCompensationLoss);
        amount(block, "compensationMental", a::setCompensationMental);
    }

    /**
     * The Forward tab's draft selection, stored as one JSON column.
     *
     * <p>Whole-block replace rather than the per-key patching the rest of this class does, because the six
     * fields are one coherent choice: the regulator name means nothing without the target that decides
     * whether a regulator is even the thing being picked. The client always sends all six, and an empty
     * block clears the draft — which is what switching the target away is.
     */
    private void applyForwardDraft(Map<String, Object> block, CepcComplaintAssessment a) {
        if (block == null) {
            return;
        }
        if (block.isEmpty()) {
            a.setForwardDraftJson(null);
            return;
        }

        Map<String, Object> draft = new LinkedHashMap<>();
        for (String key : FORWARD_DRAFT_KEYS) {
            draft.put(key, textValue(block.get(key)));
        }

        try {
            a.setForwardDraftJson(objectMapper.writeValueAsString(draft));
        } catch (JsonProcessingException e) {
            // Six short strings cannot fail to serialise, so this is unreachable in practice. Dropping the
            // draft rather than failing the whole PUT is the right trade: the same request usually carries
            // the officer's comment and the Summary fields, and losing those to a scratchpad would be worse.
            log.warn("Could not serialise the forward draft for complaint {}", a.getComplaintNumber(), e);
        }
    }

    private void applyEligibility(Map<String, Object> block, String complaintNumber,
                                  CepcComplaintAssessment assessment) {
        if (block == null) {
            return;
        }
        str(block, "proposedComplaintType", assessment::setProposedComplaintType);

        for (String key : ELIGIBILITY_KEYS) {
            if (!block.containsKey(key)) {
                continue;
            }
            Object raw = block.get(key);
            ComplaintEligibilityAnswer answer = eligibilityRepository
                    .findByComplaintNumberAndQuestionKey(complaintNumber, key)
                    .orElseGet(() -> ComplaintEligibilityAnswer.builder()
                            .complaintNumber(complaintNumber)
                            .questionKey(key)
                            .build());
            if (ELIGIBILITY_DATE_KEYS.contains(key)) {
                answer.setDateAnswer(dateValue(raw));
                answer.setBooleanAnswer(null);
            } else {
                answer.setBooleanAnswer(boolValue(raw));
                answer.setDateAnswer(null);
            }
            eligibilityRepository.save(answer);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Read
    // ════════════════════════════════════════════════════════════════════════

    private Map<String, Object> toSummary(Complaint c, String callerUserId, boolean admin) {
        CepcComplaintAssessment a = assessmentRepository
                .findByComplaintNumber(c.getComplaintNumber())
                .orElseGet(() -> CepcComplaintAssessment.builder().build());
        RegulatedEntity entity = resolveEntity(c, a);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assignedOfficer", c.getAssignedOfficer());
        out.put("canEdit", canEdit(c, callerUserId, admin));
        out.put("navBarDto", navBar(c, a, entity));
        out.put("basicDetailsDto", basicDetails(c, a));
        out.put("entityDetails", entityDetails(c, a, entity));
        out.put("complainDetailsDto", complainDetails(c, a));
        out.put("eligibility", eligibility(c, a));
        out.put("finalDecisionDto", finalDecision(c, a));
        out.put("forwardDraftDto", forwardDraft(a));
        return out;
    }

    private Map<String, Object> navBar(Complaint c, CepcComplaintAssessment a, RegulatedEntity entity) {
        Map<String, Object> nav = new LinkedHashMap<>();
        nav.put("complaintNumber", c.getComplaintNumber());
        nav.put("complaintCategory", categoryName(c));
        // Same phrasing the dashboard grid shows, from the same helper, so the two screens cannot disagree
        // about how close a complaint is to breaching.
        nav.put("slaBreachIn", CepcComplaintRowEnricher.slaBreachIn(c.getSlaDeadline(), LocalDateTime.now()));
        nav.put("status", c.getStatus());
        // Raw, like status above: COMPLAINT_SETTLED alone cannot tell "awaiting closure" apart from "marked
        // for closure", and the Final Decision tab's DO gate has to.
        nav.put("workflowStage", c.getWorkflowStage());
        // Same reason as slaBreachIn above: the header chip and the grid chip read the same helper, so they
        // cannot disagree. The raw value stays on the wire because the tab guards branch on it.
        nav.put("statusLabel", CepcStatus.label(c.getStatus(), c.getWorkflowStage()));
        nav.put("entityName", entityName(c, a, entity));
        return nav;
    }

    private Map<String, Object> basicDetails(Complaint c, CepcComplaintAssessment a) {
        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("subject", c.getSubject());
        basic.put("emailId", c.getComplainantEmail());
        basic.put("complainantName", c.getComplainantName());
        basic.put("mobile", c.getComplainantPhone());
        basic.put("receiptDate", isoDay(c.getFiledAt()));
        basic.put("modeOfReceipt", toClientFilingType(c.getFilingType()));
        basic.put("comments", a.getOfficerComments());
        basic.put("complaintCpgram", a.getComplaintCpgram());
        basic.put("cpgramNumber", a.getCpgramNumber());
        basic.put("complainDetails", c.getDescription());
        return basic;
    }

    /**
     * The entity block, reading the officer's override first and the master row only as a fallback.
     *
     * <p>Most of these have no column on {@link RegulatedEntity} at all — it carries a name, type, city and
     * state and nothing else — so for the branch, pincode, BSR code and address the assessment snapshot is
     * the only source there is.
     */
    private Map<String, Object> entityDetails(Complaint c, CepcComplaintAssessment a, RegulatedEntity e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", a.getRegulatedEntityId() != null ? a.getRegulatedEntityId()
                : (e != null ? e.getId() : null));
        details.put("entityName", entityName(c, a, e));
        // Display-only: the client reads entityType but never sends it, so it stays the master's to say.
        details.put("entityType", e != null ? e.getEntityType() : null);
        details.put("moduleName", a.getModuleName());
        details.put("entityCategory", a.getEntityCategory());
        details.put("bsrCode", a.getBsrCode());
        details.put("pincode", a.getEntityPincode());
        details.put("country", a.getEntityCountry());
        details.put("state", firstNonBlank(a.getEntityState(), e != null ? e.getState() : null));
        details.put("district", a.getEntityDistrict());
        details.put("city", firstNonBlank(a.getEntityCity(), e != null ? e.getCity() : null));
        details.put("branchName", firstNonBlank(a.getEntityBranchName(), c.getBankBranch()));
        details.put("branchCategory", a.getEntityBranchCategory());
        details.put("branchCenterName", a.getBranchCenterName());
        details.put("entityAddress", a.getEntityAddress());
        return details;
    }

    private Map<String, Object> complainDetails(Complaint c, CepcComplaintAssessment a) {
        Map<String, Object> identification = new LinkedHashMap<>();
        identification.put("otherEntityName", a.getOtherEntityName());
        identification.put("registrationWithRbiDate", isoDay(a.getRegistrationWithRbiDate()));

        Map<String, Object> classification = new LinkedHashMap<>();
        // Falls back to the master category the complaint was filed under, so a complaint the officer has
        // not reclassified shows what the complainant chose rather than an empty field.
        classification.put("complaintCategory", firstNonBlank(a.getComplaintCategoryText(), categoryName(c)));
        classification.put("complaintSubCategory1", a.getComplaintSubCategory1());
        classification.put("complaintSubCategory2", a.getComplaintSubCategory2());
        classification.put("complaintRegistrationDateValid", a.getComplaintRegistrationDateValid());
        classification.put("dateOfFilingComplaint", isoDay(a.getDateOfFilingComplaint()));

        Map<String, Object> financial = new LinkedHashMap<>();
        financial.put("reminderSent", a.getReminderSent());
        financial.put("disputedAmount", a.getDisputedAmount());
        financial.put("compensationSought", a.getCompensationSought());

        Map<String, Object> legal = new LinkedHashMap<>();
        legal.put("legalCaseFiled", a.getLegalCaseFiled());
        legal.put("preEnquiryReceived", a.getPreEnquiryReceived());
        legal.put("highPriorityComplaint", a.getHighPriorityComplaint());
        legal.put("loanDisposalAmount", a.getLoanDisposalAmount());

        Map<String, Object> additional = new LinkedHashMap<>();
        additional.put("comments", a.getAdditionalComments());
        additional.put("crpcProposedAction", a.getCrpcProposedAction());
        additional.put("proposedClause", a.getProposedClause());
        additional.put("speakingOrderContent", a.getSpeakingOrderContent());
        additional.put("vernacularLanguage", a.getVernacularLanguage());

        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("complaintRegardingPension", a.getComplaintRegardingPension());
        flags.put("complaintAgainstBusinessCorrespondent", a.getComplaintAgainstBusinessCorrespondent());
        flags.put("atmCreditDebitCard", a.getAtmCreditDebitCard());
        flags.put("schemeFlag", a.getSchemeFlag());
        flags.put("rboCgpcOld", a.getRboCgpcOld());
        flags.put("groundsFlag", a.getGroundsFlag());

        Map<String, Object> linkage = new LinkedHashMap<>();
        linkage.put("freeMarkedComplaint", a.getFreeMarkedComplaint());
        linkage.put("currentComplaintNumber", c.getComplaintNumber());
        linkage.put("replyWithin30Days", a.getReplyWithin30Days());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("basicIdentificationDto", identification);
        out.put("complaintClassification", classification);
        out.put("financialDetails", financial);
        out.put("legalCaseDetails", legal);
        out.put("additionalInformation", additional);
        out.put("flagsAndIndicators", flags);
        out.put("complaintLinkage", linkage);
        return out;
    }

    /**
     * The Final Decision tab's saved draft.
     *
     * <p>Every key is emitted whether or not a row exists, for the same reason the eligibility block below
     * does: the client reads each one by name, so an omitted key leaves the previously viewed complaint's
     * value on screen.
     */
    private Map<String, Object> finalDecision(Complaint c, CepcComplaintAssessment a) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", a.getFinalDecisionAction());

        // A clause the closure committed wins over the draft: once the complaint is closed, the draft is the
        // stale copy of the two.
        out.put("closureClause", firstNonBlank(c.getClosureClause(), a.getClosureClauseDraft()));
        out.put("closureClauseDescription", a.getClosureClauseDescription());
        out.put("complaintStatusOnPortal", a.getComplaintStatusOnPortal());
        out.put("speakingOrderGenerated", a.getSpeakingOrderGenerated());
        out.put("gistOfCase", a.getGistOfCase());
        out.put("gistOfCaseRegional", a.getGistOfCaseRegional());
        out.put("advisoryComplianceDate", isoDay(a.getAdvisoryComplianceDate()));
        out.put("disputedAmount", a.getDisputedAmount());
        out.put("compensationLoss", a.getCompensationLoss());
        out.put("compensationMental", a.getCompensationMental());
        out.put("awardImplementationDate", isoDay(a.getAwardImplementationDate()));
        out.put("awardAcceptanceDate", isoDay(a.getAwardAcceptanceDate()));
        out.put("rejectWithdrawSettleSubAction", a.getRejectWithdrawSettleSubAction());
        out.put("rejectWithdrawSettleReason", a.getRejectWithdrawSettleReason());
        return out;
    }

    /** The Forward tab's saved draft, unpacked from its JSON column into the six keys the client sends. */
    private Map<String, Object> forwardDraft(CepcComplaintAssessment a) {
        Map<String, Object> stored = Map.of();
        String json = a.getForwardDraftJson();
        if (json != null && !json.isBlank()) {
            try {
                stored = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
            } catch (JsonProcessingException e) {
                // An unreadable draft is a scratchpad, not the complaint. Returning it empty loses a
                // half-finished selection; failing the read would black out all six tabs, which key off
                // this call.
                log.warn("Could not read the forward draft for complaint {}", a.getComplaintNumber(), e);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : FORWARD_DRAFT_KEYS) {
            out.put(key, stored.get(key));
        }
        return out;
    }

    /**
     * The maintainability panel.
     *
     * <p>All 20 keys are emitted whether or not a row exists, because the client reads each one by name and
     * treats a missing key as "leave the current value alone" — so omitting the unanswered ones would leave
     * stale answers from a previously viewed complaint on screen.
     */
    private Map<String, Object> eligibility(Complaint c, CepcComplaintAssessment a) {
        Map<String, ComplaintEligibilityAnswer> stored = new LinkedHashMap<>();
        for (ComplaintEligibilityAnswer answer : eligibilityRepository
                .findByComplaintNumber(c.getComplaintNumber())) {
            stored.put(answer.getQuestionKey(), answer);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("proposedComplaintType", a.getProposedComplaintType());
        for (String key : ELIGIBILITY_KEYS) {
            ComplaintEligibilityAnswer answer = stored.get(key);
            if (ELIGIBILITY_DATE_KEYS.contains(key)) {
                out.put(key, answer == null ? null : isoDay(answer.getDateAnswer()));
            } else {
                out.put(key, answer == null ? null : answer.getBooleanAnswer());
            }
        }
        return out;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Resolution and permissions
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Accepts either the numeric id or the complaint number.
     *
     * <p>The dashboard routes to this screen by id and the detail view's own actions address the complaint
     * by number, so both forms reach this endpoint in practice.
     */
    private Complaint resolve(String idOrNumber) {
        if (idOrNumber == null || idOrNumber.isBlank()) {
            throw new ComplaintNotFoundException("No complaint was named.");
        }
        String key = idOrNumber.trim();
        Optional<Complaint> found = key.chars().allMatch(Character::isDigit)
                ? complaintRepository.findById(Long.valueOf(key))
                : complaintRepository.findByComplaintNumber(key);
        return found.orElseThrow(() ->
                new ComplaintNotFoundException("No complaint found for " + key + "."));
    }

    /** Shared with every other CEPC tab that saves — see {@link CepcEditPolicy} for why it is not local. */
    private boolean canEdit(Complaint complaint, String callerUserId, boolean admin) {
        return CepcEditPolicy.canEdit(complaint, callerUserId, admin);
    }

    /** The master row for this complaint's entity — by stored id, else by the entity name on the complaint. */
    private RegulatedEntity resolveEntity(Complaint c, CepcComplaintAssessment a) {
        if (a.getRegulatedEntityId() != null) {
            Optional<RegulatedEntity> byId = regulatedEntityRepository.findById(a.getRegulatedEntityId());
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        String name = firstNonBlank(a.getEntityName(), c.getEntityCode());
        if (name == null) {
            return null;
        }
        String normalized = RegulatedEntity.normalize(name);
        return normalized.isEmpty() ? null
                : regulatedEntityRepository.findByNameNormalized(normalized).orElse(null);
    }

    private String entityName(Complaint c, CepcComplaintAssessment a, RegulatedEntity e) {
        String fromBank = c.getBankId() == null ? null
                : bankRepository.findById(c.getBankId()).map(b -> b.getName()).orElse(null);
        return firstNonBlank(a.getEntityName(), e != null ? e.getName() : null, fromBank, c.getEntityCode());
    }

    private String categoryName(Complaint c) {
        return c.getCategoryId() == null ? null
                : categoryRepository.findById(c.getCategoryId()).map(cat -> cat.getName()).orElse(null);
    }

    private static String toClientFilingType(String stored) {
        return STORED_PORTAL.equalsIgnoreCase(stored) ? CLIENT_PORTAL : stored;
    }

    private static String toStoredFilingType(String fromClient) {
        return CLIENT_PORTAL.equalsIgnoreCase(fromClient) ? STORED_PORTAL : fromClient;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Patch primitives — each is a no-op unless the key is actually present
    // ════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> parent, String key) {
        if (parent == null) {
            return null;
        }
        Object value = parent.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static void str(Map<String, Object> block, String key, Consumer<String> setter) {
        if (block.containsKey(key)) {
            setter.accept(textValue(block.get(key)));
        }
    }

    /** The Gist fields' app-level cap — their TEXT/CLOB columns carry no length constraint of their own. */
    private static final int MAX_GIST_LENGTH = 2000;

    private static void strMax(Map<String, Object> block, String key, int maxLength, Consumer<String> setter) {
        if (!block.containsKey(key)) {
            return;
        }
        String value = textValue(block.get(key));
        if (value != null && value.length() > maxLength) {
            throw new InvalidFieldException(key + " may be at most " + maxLength + " characters.");
        }
        setter.accept(value);
    }

    /** Matches the tags/attributes/scheme a script-injection payload needs; SQL injection isn't checked here
     * because this value only ever reaches the database via a parameterized Hibernate write. */
    private static final Pattern SCRIPT_CONTENT = Pattern.compile(
            "<\\s*script|<\\s*iframe|javascript:|on\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    private static void strRequired(Map<String, Object> block, String key, int maxLength, Consumer<String> setter) {
        if (!block.containsKey(key)) {
            return;
        }
        String value = textValue(block.get(key));
        if (value == null) {
            throw new InvalidFieldException(key + " is required.");
        }
        if (value.length() > maxLength) {
            throw new InvalidFieldException(key + " may be at most " + maxLength + " characters.");
        }
        setter.accept(value);
    }

    private static void strRequiredRange(Map<String, Object> block, String key, int minLength, int maxLength,
                                         Consumer<String> setter) {
        if (!block.containsKey(key)) {
            return;
        }
        String value = textValue(block.get(key));
        if (value == null) {
            throw new InvalidFieldException(key + " is required.");
        }
        if (value.length() < minLength || value.length() > maxLength) {
            throw new InvalidFieldException(key + " must be between " + minLength + " and " + maxLength + " characters.");
        }
        if (SCRIPT_CONTENT.matcher(value).find()) {
            throw new InvalidFieldException(key + " may not contain script content.");
        }
        setter.accept(value);
    }

    private static void flag(Map<String, Object> block, String key, Consumer<Boolean> setter) {
        if (block.containsKey(key)) {
            setter.accept(boolValue(block.get(key)));
        }
    }

    private static void num(Map<String, Object> block, String key, Consumer<Integer> setter) {
        if (block.containsKey(key)) {
            setter.accept(intValue(block.get(key)));
        }
    }

    private static void amount(Map<String, Object> block, String key, Consumer<BigDecimal> setter) {
        if (block.containsKey(key)) {
            setter.accept(decimalValue(block.get(key)));
        }
    }

    private static void day(Map<String, Object> block, String key, Consumer<LocalDate> setter) {
        if (block.containsKey(key)) {
            setter.accept(dateValue(block.get(key)));
        }
    }

    private static String textValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Boolean boolValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private static Integer intValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimalValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal d) {
            return d;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String text = raw.toString().trim().replace(",", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a date the client may have sent as {@code yyyy-MM-dd}, as a full ISO timestamp, or day-first.
     *
     * <p>An unparseable value becomes null rather than an error, matching how the dashboard search treats
     * bad dates. These arrive from date pickers, so a malformed one is a sign of a stale draft rather than
     * something the officer can act on, and rejecting the whole save would strand every other field on the
     * form.
     */
    private static LocalDate dateValue(Object raw) {
        String text = textValue(raw);
        if (text == null) {
            return null;
        }
        String candidate = text.length() > 10 && (text.charAt(10) == 'T' || text.charAt(10) == ' ')
                ? text.substring(0, 10) : text;
        try {
            return LocalDate.parse(candidate);
        } catch (Exception ignored) {
            // fall through to the day-first form
        }
        try {
            return LocalDate.parse(candidate, DAY_FIRST);
        } catch (Exception e) {
            log.debug("Ignoring unparseable date on the CEPC summary: {}", text);
            return null;
        }
    }

    private static String isoDay(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String isoDay(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toLocalDate().toString();
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
