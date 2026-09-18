package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAdditionalDetail;
import com.hrms.cms.entity.ComplaintEligibilityAnswer;
import com.hrms.cms.entity.ComplaintRbioFormData;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.repository.ComplaintAdditionalDetailRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintEligibilityAnswerRepository;
import com.hrms.cms.repository.ComplaintRbioFormDataRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Assembles and applies edits to the consolidated RBIO complaint summary.
 * <p>
 * The sections are stitched from four tables: COMPLAINTS plus the three per-complaint child rows
 * written at registration. Any child row may be absent - {@code ComplaintService.fileComplaint}
 * skips them when the wizard sent no {@code formData} - so every read null-guards and every write
 * upserts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioComplaintSummaryService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintEligibilityAnswerRepository eligibilityRepository;
    private final ComplaintAdditionalDetailRepository additionalDetailRepository;
    private final ComplaintRbioFormDataRepository formDataRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final RbioSlaService rbioSlaService;
    private final ComplaintService complaintService;
    private final CepcAuditService auditService;

    private static final Set<String> SECTION_KEYS = Set.of(
            "navBarDto", "basicDetailsDto", "eligibility", "entityDetails", "complainDetailsDto");

    private static final Set<String> NESTED_DETAIL_KEYS = Set.of(
            "basicIdentificationDto", "complaintClassification", "financialDetails",
            "legalCaseDetails", "additionalInformation", "flagsAndIndicators", "complaintLinkage");

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(Long complaintId) {
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        ComplaintEligibilityAnswer elig = eligibilityRepository.findByComplaintId(complaintId).orElse(null);
        ComplaintAdditionalDetail add = additionalDetailRepository.findByComplaintId(complaintId).orElse(null);
        ComplaintRbioFormData fd = formDataRepository.findByComplaintId(complaintId).orElse(null);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", c.getId());
        summary.put("navBarDto", navBar(c));
        summary.put("basicDetailsDto", basicDetails(c, fd));
        summary.put("eligibility", eligibility(c, elig, add, fd));
        summary.put("entityDetails", entityDetails(c, fd));
        summary.put("complainDetailsDto", complainDetails(c, elig, add, fd));
        return summary;
    }

    private Map<String, Object> navBar(Complaint c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("complaintNumber", c.getComplaintNumber());
        m.put("complainantName", c.getComplainantName());
        m.put("entityName", c.getEntityName());
        m.put("status", c.getStatus());
        m.put("complaintCategory", categoryName(c));
        m.put("slaBreachIn", rbioSlaService.formatBreachIn(c));
        return m;
    }

    private Map<String, Object> basicDetails(Complaint c, ComplaintRbioFormData fd) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", fd != null ? fd.getId() : null);
        m.put("subject", c.getSubject());
        m.put("emailId", c.getComplainantEmail());
        m.put("complainantName", c.getComplainantName());
        m.put("receiptDate", fd != null ? fd.getReceiptDate() : null);
        m.put("modeOfReceipt", fd != null ? fd.getModeOfReceipt() : null);
        m.put("comments", fd != null ? fd.getComments() : null);
        m.put("complaintCpgram", fd != null ? yesNo(fd.getComplaintCpgram()) : null);
        m.put("cpgramNumber", fd != null ? fd.getCpgramNumber() : null);
        m.put("complainDetails", c.getDescription());
        return m;
    }

    private Map<String, Object> eligibility(Complaint c, ComplaintEligibilityAnswer e,
                                            ComplaintAdditionalDetail add, ComplaintRbioFormData fd) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("proposedComplaintType", e != null ? e.getProposedComplaintType() : null);
        m.put("entityRegulatedByRbi", e != null ? yesNo(e.getEntityRegulatedByRbi()) : null);
        m.put("complaintNotDirectlyAddressedToOmbudsman",
                e != null ? yesNo(e.getComplaintNotDirectlyAddressedToOmbudsman()) : null);
        m.put("complaintNotRegisteredWithEntity", e != null ? yesNo(e.getComplaintNotRegisteredWithEntity()) : null);
        m.put("frivolousVexatiousThreatening", e != null ? yesNo(e.getFrivolousVexatiousThreatening()) : null);
        m.put("subJudiceOrArbitration", e != null ? yesNo(e.getIsSubJudice()) : null);
        m.put("sameGrievancePendingBeforeCourt", e != null ? yesNo(e.getSameGrievancePendingBeforeCourt()) : null);
        m.put("sameGrievanceSettledBeforeCourt", e != null ? yesNo(e.getSameGrievanceSettledBeforeCourt()) : null);
        m.put("complaintMadeThroughAdvocate", e != null ? yesNo(e.getThroughAdvocate()) : null);
        m.put("complainantIsAdvocate", e != null ? yesNo(e.getComplainantIsAdvocate()) : null);
        m.put("sameGrievancePendingBeforeOmbudsman", e != null ? yesNo(e.getPendingBeforeOmbudsman()) : null);
        m.put("alreadyDealtWithByOmbudsman", e != null ? yesNo(e.getSettledByOmbudsman()) : null);
        m.put("complaintAgainstManagement", e != null ? yesNo(e.getComplaintAgainstManagement()) : null);
        // The officer's own answer wins; fall back to what the public wizard recorded.
        Boolean filedWithCepc = e != null ? yesNo(e.getComplaintFiledWithCepcOrRbi()) : null;
        if (filedWithCepc == null && e != null) filedWithCepc = yesNo(e.getPreviouslyFiledWithCepc());
        m.put("complaintFiledWithCEPCOrRBI", filedWithCepc);
        m.put("disputeBetweenREs", e != null ? yesNo(e.getDisputeBetweenRes()) : null);
        m.put("completeInformationUnavailable", e != null ? yesNo(e.getCompleteInformationUnavailable()) : null);
        m.put("writtenComplaintFiledWithRE", e != null ? yesNo(e.getFiledWithRe()) : null);
        m.put("firstFiledWithREDate", e != null ? e.getFirstFiledWithReDate() : null);
        m.put("receivedReplyFromEntity", e != null ? yesNo(e.getReceivedReply()) : null);
        m.put("replyDate", add != null ? add.getReplyDate() : null);
        return m;
    }

    private Map<String, Object> entityDetails(Complaint c, ComplaintRbioFormData fd) {
        String entityType = c.getRegulatedEntityId() != null
                ? regulatedEntityRepository.findById(c.getRegulatedEntityId())
                        .map(re -> re.getEntityType()).orElse(null)
                : null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getRegulatedEntityId());
        m.put("entityName", c.getEntityName());
        m.put("moduleName", fd != null ? fd.getModuleName() : null);
        m.put("entityCategory", c.getEntityCategory());
        m.put("entityType", entityType);
        m.put("bsrCode", c.getEntityBsrCode());
        m.put("pincode", c.getEntityPincode());
        m.put("country", fd != null ? fd.getEntityCountry() : null);
        m.put("state", c.getEntityState());
        m.put("district", c.getEntityDistrict());
        m.put("city", c.getEntityCity());
        m.put("branchName", c.getEntityBranchName());
        m.put("branchCategory", c.getEntityBranchCategory());
        m.put("branchCenterName", fd != null ? fd.getBranchCenterName() : null);
        m.put("entityAddress", c.getEntityAddress());
        return m;
    }

    private Map<String, Object> complainDetails(Complaint c, ComplaintEligibilityAnswer e,
                                                ComplaintAdditionalDetail add, ComplaintRbioFormData fd) {
        Long fdId = fd != null ? fd.getId() : null;
        LocalDate dateOfFiling = fd != null ? fd.getDateOfFilingComplaint() : null;

        Map<String, Object> basicId = new LinkedHashMap<>();
        basicId.put("emailId", c.getComplainantEmail());
        basicId.put("entityName", c.getEntityName());
        basicId.put("otherEntityName", fd != null ? fd.getOtherEntityName() : null);
        basicId.put("registrationWithRbiDate", fd != null ? fd.getRegistrationWithRbiDate() : null);

        Map<String, Object> classification = new LinkedHashMap<>();
        classification.put("id", c.getCategoryId());
        classification.put("complaintCategory", categoryName(c));
        classification.put("complaintSubCategory1", add != null ? add.getSubCategory1() : null);
        classification.put("complaintSubCategory2", add != null ? add.getSubCategory2() : null);
        classification.put("complaintRegistrationDateValid",
                fd != null ? yesNo(fd.getComplaintRegistrationDateValid()) : null);
        classification.put("dateOfFilingComplaint", dateOfFiling);

        Map<String, Object> financial = new LinkedHashMap<>();
        financial.put("id", add != null ? add.getId() : null);
        financial.put("reminderSent", e != null ? yesNo(e.getSentReminder()) : null);
        financial.put("disputedAmount", c.getAmountInvolved());
        financial.put("compensationSought", add != null ? add.getCompensationSought() : null);
        financial.put("dateOfFiling", dateOfFiling);

        Map<String, Object> legal = new LinkedHashMap<>();
        legal.put("id", fdId);
        legal.put("legalCaseFiled", fd != null ? yesNo(fd.getLegalCaseFiled()) : null);
        legal.put("filingDate", fd != null ? fd.getLegalFilingDate() : null);
        legal.put("preEnquiryReceived", fd != null ? yesNo(fd.getPreEnquiryReceived()) : null);
        legal.put("highPriorityComplaint", fd != null ? yesNo(fd.getHighPriorityComplaint()) : null);
        legal.put("loanDisposalAmount", fd != null ? fd.getLoanDisposalAmount() : null);

        Map<String, Object> additional = new LinkedHashMap<>();
        additional.put("id", fdId);
        additional.put("comments", fd != null ? fd.getComments() : null);
        additional.put("crpcProposedAction", c.getProposedAction());
        additional.put("vernacularLanguage", fd != null ? fd.getVernacularLanguage() : null);
        additional.put("dateOfFiling", dateOfFiling);

        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("id", fdId);
        flags.put("complaintRegardingPension", fd != null ? yesNo(fd.getComplaintRegardingPension()) : null);
        flags.put("complaintAgainstBusinessCorrespondent",
                add != null ? yesNo(add.getIsBusinessCorrespondent()) : null);
        flags.put("atmCreditDebitCard", fd != null ? yesNo(fd.getAtmCreditDebitCard()) : null);
        flags.put("schemeFlag", fd != null ? fd.getSchemeFlag() : null);
        flags.put("rboCgpcOld", fd != null ? fd.getRboCgpcOld() : null);
        flags.put("groundsFlag", fd != null ? fd.getGroundsFlag() : null);

        Map<String, Object> linkage = new LinkedHashMap<>();
        linkage.put("id", fdId);
        linkage.put("freeMarkedComplaint", fd != null ? yesNo(fd.getFreeMarkedComplaint()) : null);
        linkage.put("currentComplaintNumber", c.getComplaintNumber());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("basicIdentificationDto", basicId);
        m.put("complaintClassification", classification);
        m.put("financialDetails", financial);
        m.put("legalCaseDetails", legal);
        m.put("additionalInformation", additional);
        m.put("flagsAndIndicators", flags);
        m.put("complaintLinkage", linkage);
        return m;
    }

    private String categoryName(Complaint c) {
        if (c.getCategoryName() != null && !c.getCategoryName().isBlank()) return c.getCategoryName();
        if (c.getCategoryId() == null) return null;
        return categoryRepository.findById(c.getCategoryId()).map(cat -> cat.getName()).orElse(null);
    }

    // ---------------------------------------------------------------- write

    /**
     * Apply an officer's edits. Accepts the same nested shape {@link #getSummary} returns; a section
     * or field that is absent is left untouched, while a field present with a {@code null} value is
     * cleared. That distinction is why this takes a Map rather than a typed DTO.
     */
    @Transactional
    @CacheEvict(value = "dashboard", allEntries = true)
    public Map<String, Object> updateSummary(Long complaintId, Map<String, Object> payload, String actor) {
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        validateSections(payload);

        Map<String, Object> navBar = section(payload, "navBarDto");
        Map<String, Object> basic = section(payload, "basicDetailsDto");
        Map<String, Object> elig = section(payload, "eligibility");
        Map<String, Object> entity = section(payload, "entityDetails");
        Map<String, Object> details = section(payload, "complainDetailsDto");

        Map<String, Object> basicId = section(details, "basicIdentificationDto");
        Map<String, Object> classification = section(details, "complaintClassification");
        Map<String, Object> financial = section(details, "financialDetails");
        Map<String, Object> legal = section(details, "legalCaseDetails");
        Map<String, Object> additional = section(details, "additionalInformation");
        Map<String, Object> flags = section(details, "flagsAndIndicators");
        Map<String, Object> linkage = section(details, "complaintLinkage");

        applyToComplaint(c, navBar, basic, entity, classification, financial, additional);

        if (!elig.isEmpty() || financial.containsKey("reminderSent")) {
            applyToEligibility(complaintId, elig, financial);
        }
        if (!classification.isEmpty() || !financial.isEmpty() || !elig.isEmpty() || !flags.isEmpty()) {
            applyToAdditionalDetail(complaintId, classification, financial, elig, flags);
        }
        applyToFormData(complaintId, basic, entity, basicId, classification, legal, additional, flags, linkage);

        complaintRepository.save(c);

        complaintService.addTimeline(complaintId, "rbio_summary_updated", actor,
                "RBIO officer updated complaint summary", c.getStatus(), c.getStatus());
        auditService.logAction(c.getComplaintNumber(), "RBIO_SUMMARY_UPDATED", actor, "RBIO",
                "Summary fields edited from the RBIO portal",
                Map.of("sections", payload.keySet(), "complaintId", complaintId));

        return getSummary(complaintId);
    }

    private void validateSections(Map<String, Object> payload) {
        for (String key : payload.keySet()) {
            if (!SECTION_KEYS.contains(key) && !"id".equals(key)) {
                throw new IllegalArgumentException("Unknown section: " + key);
            }
        }
        Object details = payload.get("complainDetailsDto");
        if (details instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (!NESTED_DETAIL_KEYS.contains(String.valueOf(key))) {
                    throw new IllegalArgumentException("Unknown complainDetailsDto section: " + key);
                }
            }
        }
    }

    private void applyToComplaint(Complaint c, Map<String, Object> navBar, Map<String, Object> basic,
                                  Map<String, Object> entity, Map<String, Object> classification,
                                  Map<String, Object> financial, Map<String, Object> additional) {
        setIfPresent(navBar, "status", v -> c.setStatus(str(v, "status")));
        setIfPresent(navBar, "complainantName", v -> c.setComplainantName(str(v, "complainantName")));
        setIfPresent(navBar, "entityName", v -> c.setEntityName(str(v, "entityName")));

        setIfPresent(basic, "subject", v -> c.setSubject(str(v, "subject")));
        setIfPresent(basic, "emailId", v -> c.setComplainantEmail(str(v, "emailId")));
        setIfPresent(basic, "complainantName", v -> c.setComplainantName(str(v, "complainantName")));
        setIfPresent(basic, "complainDetails", v -> c.setDescription(str(v, "complainDetails")));

        setIfPresent(entity, "entityName", v -> c.setEntityName(str(v, "entityName")));
        setIfPresent(entity, "entityCategory", v -> c.setEntityCategory(str(v, "entityCategory")));
        setIfPresent(entity, "bsrCode", v -> c.setEntityBsrCode(str(v, "bsrCode")));
        setIfPresent(entity, "pincode", v -> c.setEntityPincode(str(v, "pincode")));
        setIfPresent(entity, "state", v -> c.setEntityState(str(v, "state")));
        setIfPresent(entity, "district", v -> c.setEntityDistrict(str(v, "district")));
        setIfPresent(entity, "city", v -> c.setEntityCity(str(v, "city")));
        setIfPresent(entity, "branchName", v -> c.setEntityBranchName(str(v, "branchName")));
        setIfPresent(entity, "branchCategory", v -> c.setEntityBranchCategory(str(v, "branchCategory")));
        setIfPresent(entity, "entityAddress", v -> c.setEntityAddress(str(v, "entityAddress")));
        setIfPresent(entity, "id", v -> c.setRegulatedEntityId(longVal(v, "entityDetails.id")));

        setIfPresent(classification, "complaintCategory", v -> c.setCategoryName(str(v, "complaintCategory")));
        setIfPresent(financial, "disputedAmount", v -> c.setAmountInvolved(decimal(v, "disputedAmount")));
        setIfPresent(additional, "crpcProposedAction", v -> c.setProposedAction(str(v, "crpcProposedAction")));
    }

    private void applyToEligibility(Long complaintId, Map<String, Object> e, Map<String, Object> financial) {
        ComplaintEligibilityAnswer row = eligibilityRepository.findByComplaintId(complaintId)
                .orElseGet(() -> ComplaintEligibilityAnswer.builder().complaintId(complaintId).build());

        setIfPresent(e, "proposedComplaintType", v -> row.setProposedComplaintType(str(v, "proposedComplaintType")));
        setIfPresent(e, "entityRegulatedByRbi", v -> row.setEntityRegulatedByRbi(toYesNo(v, "entityRegulatedByRbi")));
        setIfPresent(e, "complaintNotDirectlyAddressedToOmbudsman",
                v -> row.setComplaintNotDirectlyAddressedToOmbudsman(toYesNo(v, "complaintNotDirectlyAddressedToOmbudsman")));
        setIfPresent(e, "complaintNotRegisteredWithEntity",
                v -> row.setComplaintNotRegisteredWithEntity(toYesNo(v, "complaintNotRegisteredWithEntity")));
        setIfPresent(e, "frivolousVexatiousThreatening",
                v -> row.setFrivolousVexatiousThreatening(toYesNo(v, "frivolousVexatiousThreatening")));
        setIfPresent(e, "subJudiceOrArbitration", v -> row.setIsSubJudice(toYesNo(v, "subJudiceOrArbitration")));
        setIfPresent(e, "sameGrievancePendingBeforeCourt",
                v -> row.setSameGrievancePendingBeforeCourt(toYesNo(v, "sameGrievancePendingBeforeCourt")));
        setIfPresent(e, "sameGrievanceSettledBeforeCourt",
                v -> row.setSameGrievanceSettledBeforeCourt(toYesNo(v, "sameGrievanceSettledBeforeCourt")));
        setIfPresent(e, "complaintMadeThroughAdvocate",
                v -> row.setThroughAdvocate(toYesNo(v, "complaintMadeThroughAdvocate")));
        setIfPresent(e, "complainantIsAdvocate", v -> row.setComplainantIsAdvocate(toYesNo(v, "complainantIsAdvocate")));
        setIfPresent(e, "sameGrievancePendingBeforeOmbudsman",
                v -> row.setPendingBeforeOmbudsman(toYesNo(v, "sameGrievancePendingBeforeOmbudsman")));
        setIfPresent(e, "alreadyDealtWithByOmbudsman",
                v -> row.setSettledByOmbudsman(toYesNo(v, "alreadyDealtWithByOmbudsman")));
        setIfPresent(e, "complaintAgainstManagement",
                v -> row.setComplaintAgainstManagement(toYesNo(v, "complaintAgainstManagement")));
        setIfPresent(e, "complaintFiledWithCEPCOrRBI",
                v -> row.setComplaintFiledWithCepcOrRbi(toYesNo(v, "complaintFiledWithCEPCOrRBI")));
        setIfPresent(e, "disputeBetweenREs", v -> row.setDisputeBetweenRes(toYesNo(v, "disputeBetweenREs")));
        setIfPresent(e, "completeInformationUnavailable",
                v -> row.setCompleteInformationUnavailable(toYesNo(v, "completeInformationUnavailable")));
        setIfPresent(e, "writtenComplaintFiledWithRE", v -> row.setFiledWithRe(toYesNo(v, "writtenComplaintFiledWithRE")));
        setIfPresent(e, "firstFiledWithREDate", v -> row.setFirstFiledWithReDate(date(v, "firstFiledWithREDate")));
        setIfPresent(e, "receivedReplyFromEntity", v -> row.setReceivedReply(toYesNo(v, "receivedReplyFromEntity")));
        setIfPresent(financial, "reminderSent", v -> row.setSentReminder(toYesNo(v, "reminderSent")));

        eligibilityRepository.save(row);
    }

    private void applyToAdditionalDetail(Long complaintId, Map<String, Object> classification,
                                         Map<String, Object> financial, Map<String, Object> elig,
                                         Map<String, Object> flags) {
        ComplaintAdditionalDetail row = additionalDetailRepository.findByComplaintId(complaintId)
                .orElseGet(() -> ComplaintAdditionalDetail.builder().complaintId(complaintId).build());

        setIfPresent(classification, "complaintSubCategory1", v -> row.setSubCategory1(str(v, "complaintSubCategory1")));
        setIfPresent(classification, "complaintSubCategory2", v -> row.setSubCategory2(str(v, "complaintSubCategory2")));
        setIfPresent(financial, "compensationSought", v -> row.setCompensationSought(decimal(v, "compensationSought")));
        setIfPresent(elig, "replyDate", v -> row.setReplyDate(date(v, "replyDate")));
        setIfPresent(flags, "complaintAgainstBusinessCorrespondent",
                v -> row.setIsBusinessCorrespondent(toYesNo(v, "complaintAgainstBusinessCorrespondent")));

        additionalDetailRepository.save(row);
    }

    private void applyToFormData(Long complaintId, Map<String, Object> basic, Map<String, Object> entity,
                                 Map<String, Object> basicId, Map<String, Object> classification,
                                 Map<String, Object> legal, Map<String, Object> additional,
                                 Map<String, Object> flags, Map<String, Object> linkage) {
        ComplaintRbioFormData row = formDataRepository.findByComplaintId(complaintId)
                .orElseGet(() -> ComplaintRbioFormData.builder().complaintId(complaintId).build());

        setIfPresent(basic, "receiptDate", v -> row.setReceiptDate(date(v, "receiptDate")));
        setIfPresent(basic, "modeOfReceipt", v -> row.setModeOfReceipt(str(v, "modeOfReceipt")));
        setIfPresent(basic, "comments", v -> row.setComments(str(v, "comments")));
        setIfPresent(basic, "complaintCpgram", v -> row.setComplaintCpgram(toYesNo(v, "complaintCpgram")));
        setIfPresent(basic, "cpgramNumber", v -> row.setCpgramNumber(str(v, "cpgramNumber")));

        setIfPresent(entity, "moduleName", v -> row.setModuleName(str(v, "moduleName")));
        setIfPresent(entity, "country", v -> row.setEntityCountry(str(v, "country")));
        setIfPresent(entity, "branchCenterName", v -> row.setBranchCenterName(str(v, "branchCenterName")));

        setIfPresent(basicId, "otherEntityName", v -> row.setOtherEntityName(str(v, "otherEntityName")));
        setIfPresent(basicId, "registrationWithRbiDate",
                v -> row.setRegistrationWithRbiDate(date(v, "registrationWithRbiDate")));

        setIfPresent(classification, "complaintRegistrationDateValid",
                v -> row.setComplaintRegistrationDateValid(toYesNo(v, "complaintRegistrationDateValid")));
        setIfPresent(classification, "dateOfFilingComplaint",
                v -> row.setDateOfFilingComplaint(date(v, "dateOfFilingComplaint")));

        setIfPresent(legal, "legalCaseFiled", v -> row.setLegalCaseFiled(toYesNo(v, "legalCaseFiled")));
        setIfPresent(legal, "filingDate", v -> row.setLegalFilingDate(date(v, "filingDate")));
        setIfPresent(legal, "preEnquiryReceived", v -> row.setPreEnquiryReceived(toYesNo(v, "preEnquiryReceived")));
        setIfPresent(legal, "highPriorityComplaint",
                v -> row.setHighPriorityComplaint(toYesNo(v, "highPriorityComplaint")));
        setIfPresent(legal, "loanDisposalAmount", v -> row.setLoanDisposalAmount(decimal(v, "loanDisposalAmount")));

        setIfPresent(additional, "comments", v -> row.setComments(str(v, "comments")));
        setIfPresent(additional, "vernacularLanguage", v -> row.setVernacularLanguage(str(v, "vernacularLanguage")));

        setIfPresent(flags, "complaintRegardingPension",
                v -> row.setComplaintRegardingPension(toYesNo(v, "complaintRegardingPension")));
        setIfPresent(flags, "atmCreditDebitCard", v -> row.setAtmCreditDebitCard(toYesNo(v, "atmCreditDebitCard")));
        setIfPresent(flags, "schemeFlag", v -> row.setSchemeFlag(str(v, "schemeFlag")));
        setIfPresent(flags, "rboCgpcOld", v -> row.setRboCgpcOld(str(v, "rboCgpcOld")));
        setIfPresent(flags, "groundsFlag", v -> row.setGroundsFlag(str(v, "groundsFlag")));

        setIfPresent(linkage, "freeMarkedComplaint",
                v -> row.setFreeMarkedComplaint(toYesNo(v, "freeMarkedComplaint")));

        formDataRepository.save(row);
    }

    // ---------------------------------------------------------------- backfill

    /**
     * Carry the CRPC email-draft fields that COMPLAINTS has no column for into the officer-editable
     * row at draft->complaint conversion. Without this they are silently dropped and the officer
     * re-keys data the DEO already entered.
     * <p>
     * Every date and amount on {@code EmailDraft} is a free-text String, so anything unparseable is
     * stored as null rather than failing the conversion.
     */
    @Transactional
    public void backfillFromEmailDraft(EmailDraft draft, Long complaintId) {
        if (draft == null || complaintId == null) return;

        ComplaintRbioFormData row = formDataRepository.findByComplaintId(complaintId)
                .orElseGet(() -> ComplaintRbioFormData.builder().complaintId(complaintId).build());

        row.setDraftId(draft.getDraftId());
        row.setReceiptDate(draft.getReceivedAt() != null ? draft.getReceivedAt().toLocalDate() : null);
        row.setModeOfReceipt(draft.getModeOfReceipt());
        row.setComments(draft.getAdditionalComments());
        row.setCpgramNumber(draft.getCpgramsNumber());
        row.setEntityCountry(draft.getEntityCountry());
        row.setBranchCenterName(draft.getEntityBranchCenterName());
        row.setOtherEntityName(draft.getOtherEntityName());
        row.setRegistrationWithRbiDate(looseDate(draft.getDateOfRegistrationWithRBI()));
        row.setComplaintRegistrationDateValid(looseYesNo(draft.getComplaintRegDateValid()));
        row.setDateOfFilingComplaint(looseDate(draft.getDateOfFilingComplaint()));
        row.setLegalCaseFiled(looseYesNo(draft.getLegalCaseFiled()));
        row.setLegalFilingDate(looseDate(draft.getLegalDateOfFiling()));
        row.setPreEnquiryReceived(looseYesNo(draft.getPreEnquiryReceived()));
        row.setHighPriorityComplaint(looseYesNo(draft.getHighPriorityComplaint()));
        row.setLoanDisposalAmount(looseDecimal(draft.getLoanDisposalAmount()));
        row.setVernacularLanguage(draft.getVernacularLanguageDetail());
        row.setComplaintRegardingPension(looseYesNo(draft.getIsRegardingPension()));
        row.setAtmCreditDebitCard(looseYesNo(draft.getIsAtmCreditDebitCard()));
        row.setSchemeFlag(draft.getSchemeFlag());
        row.setFreeMarkedComplaint(looseYesNo(draft.getIsFreeMarkedComplaint()));

        formDataRepository.save(row);
    }

    private static LocalDate looseDate(String v) {
        if (v == null || v.isBlank() || v.length() < 10) return null;
        try {
            return LocalDate.parse(v.trim().substring(0, 10));
        } catch (DateTimeParseException ex) {
            log.debug("Draft carried an unparseable date, storing null: {}", v);
            return null;
        }
    }

    private static BigDecimal looseDecimal(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return new BigDecimal(v.trim().replace(",", ""));
        } catch (NumberFormatException ex) {
            log.debug("Draft carried an unparseable amount, storing null: {}", v);
            return null;
        }
    }

    private static String looseYesNo(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim();
        if ("yes".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s)) return "yes";
        if ("no".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s) || "n".equalsIgnoreCase(s)) return "no";
        return null;
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String key) {
        Object v = parent.get(key);
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }

    /** Absent key = leave alone; present key with a null value = clear. */
    private static void setIfPresent(Map<String, Object> src, String key, Consumer<Object> setter) {
        if (src.containsKey(key)) setter.accept(src.get(key));
    }

    /** {@code "yes"}/{@code "no"}/NULL as stored -> JSON tri-state, keeping "never asked" distinct. */
    static Boolean yesNo(String v) {
        if (v == null || v.isBlank()) return null;
        return "yes".equalsIgnoreCase(v.trim());
    }

    static String toYesNo(Object v, String field) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b ? "yes" : "no";
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if ("yes".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s)) return "yes";
        if ("no".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) return "no";
        throw new IllegalArgumentException("Field '" + field + "' must be true, false or null");
    }

    private static String str(Object v, String field) {
        if (v == null) return null;
        if (v instanceof String s) return s;
        throw new IllegalArgumentException("Field '" + field + "' must be a string");
    }

    private static LocalDate date(Object v, String field) {
        if (v == null) return null;
        try {
            return LocalDate.parse(String.valueOf(v).substring(0, 10));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("Field '" + field + "' must be an ISO date (yyyy-MM-dd)");
        }
    }

    private static BigDecimal decimal(Object v, String field) {
        if (v == null) return null;
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Field '" + field + "' must be a number");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Field '" + field + "' must not be negative");
        }
        return amount;
    }

    private static Long longVal(Object v, String field) {
        if (v == null) return null;
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Field '" + field + "' must be a number");
        }
    }
}
