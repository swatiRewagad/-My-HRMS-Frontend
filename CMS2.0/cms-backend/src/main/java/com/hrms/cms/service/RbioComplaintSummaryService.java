package com.hrms.cms.service;

import com.hrms.cms.dto.complaint.RbioComplaintSummaryResponse;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAdditionalDetail;
import com.hrms.cms.entity.ComplaintEligibilityAnswer;
import com.hrms.cms.entity.ComplaintRbioFormData;
import com.hrms.cms.entity.ComplaintReadReceipt;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.ComplaintAdditionalDetailRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintEligibilityAnswerRepository;
import com.hrms.cms.repository.ComplaintRbioFormDataRepository;
import com.hrms.cms.repository.ComplaintReadReceiptRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
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
    private final ComplaintReadReceiptRepository readReceiptRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final RbioSlaService rbioSlaService;
    private final ComplaintService complaintService;
    private final CepcAuditService auditService;
    private final RbioHierarchyService rbioHierarchyService;

    private static final Set<String> SECTION_KEYS = Set.of(
            "navBarDto", "basicDetailsDto", "eligibility", "entityDetails", "complainDetailsDto");

    private static final Set<String> NESTED_DETAIL_KEYS = Set.of(
            "basicIdentificationDto", "complaintClassification", "financialDetails",
            "legalCaseDetails", "additionalInformation", "flagsAndIndicators", "complaintLinkage");

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public RbioComplaintSummaryResponse getSummary(Long complaintId) {
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        ComplaintEligibilityAnswer elig = eligibilityRepository.findByComplaintId(complaintId).orElse(null);
        ComplaintAdditionalDetail add = additionalDetailRepository.findByComplaintId(complaintId).orElse(null);
        ComplaintRbioFormData fd = formDataRepository.findByComplaintId(complaintId).orElse(null);

        return RbioComplaintSummaryResponse.builder()
                .id(c.getId())
                // Surfaced at the top level because the screens use it to decide between an editable form
                // and a read-only view; only the holder may write (see RbioHierarchyService#canEdit).
                .assignedOfficer(c.getAssignedOfficer())
                .assignedOfficerName(c.getAssignedOfficerName())
                .assignedRole(c.getAssignedRole())
                .navBarDto(navBar(c))
                .basicDetailsDto(basicDetails(c, fd))
                .eligibility(eligibility(c, elig, add))
                .entityDetails(entityDetails(c, fd))
                .complainDetailsDto(complainDetails(c, elig, add, fd))
                .build();
    }

    private RbioComplaintSummaryResponse.NavBar navBar(Complaint c) {
        return RbioComplaintSummaryResponse.NavBar.builder()
                .complaintNumber(c.getComplaintNumber())
                .complainantName(c.getComplainantName())
                .entityName(c.getEntityName())
                .status(c.getStatus())
                .complaintCategory(categoryName(c))
                .slaBreachIn(rbioSlaService.formatBreachIn(c))
                .build();
    }

    private RbioComplaintSummaryResponse.BasicDetails basicDetails(Complaint c, ComplaintRbioFormData fd) {
        return RbioComplaintSummaryResponse.BasicDetails.builder()
                .id(fd != null ? fd.getId() : null)
                .subject(c.getSubject())
                .emailId(c.getComplainantEmail())
                .complainantName(c.getComplainantName())
                .receiptDate(fd != null ? fd.getReceiptDate() : null)
                .modeOfReceipt(fd != null ? fd.getModeOfReceipt() : null)
                .comments(fd != null ? fd.getComments() : null)
                .complaintCpgram(fd != null ? yesNo(fd.getComplaintCpgram()) : null)
                .cpgramNumber(fd != null ? fd.getCpgramNumber() : null)
                .complainDetails(c.getDescription())
                .build();
    }

    private RbioComplaintSummaryResponse.Eligibility eligibility(Complaint c, ComplaintEligibilityAnswer e,
                                                                 ComplaintAdditionalDetail add) {
        // The officer's own answer wins; fall back to what the public wizard recorded.
        Boolean filedWithCepc = e != null ? yesNo(e.getComplaintFiledWithCepcOrRbi()) : null;
        if (filedWithCepc == null && e != null) filedWithCepc = yesNo(e.getPreviouslyFiledWithCepc());

        return RbioComplaintSummaryResponse.Eligibility.builder()
                .id(c.getId())
                .proposedComplaintType(e != null ? e.getProposedComplaintType() : null)
                .entityRegulatedByRbi(e != null ? yesNo(e.getEntityRegulatedByRbi()) : null)
                .complaintNotDirectlyAddressedToOmbudsman(
                        e != null ? yesNo(e.getComplaintNotDirectlyAddressedToOmbudsman()) : null)
                .complaintNotRegisteredWithEntity(e != null ? yesNo(e.getComplaintNotRegisteredWithEntity()) : null)
                .frivolousVexatiousThreatening(e != null ? yesNo(e.getFrivolousVexatiousThreatening()) : null)
                .subJudiceOrArbitration(e != null ? yesNo(e.getIsSubJudice()) : null)
                .sameGrievancePendingBeforeCourt(e != null ? yesNo(e.getSameGrievancePendingBeforeCourt()) : null)
                .sameGrievanceSettledBeforeCourt(e != null ? yesNo(e.getSameGrievanceSettledBeforeCourt()) : null)
                .complaintMadeThroughAdvocate(e != null ? yesNo(e.getThroughAdvocate()) : null)
                .complainantIsAdvocate(e != null ? yesNo(e.getComplainantIsAdvocate()) : null)
                .sameGrievancePendingBeforeOmbudsman(e != null ? yesNo(e.getPendingBeforeOmbudsman()) : null)
                .alreadyDealtWithByOmbudsman(e != null ? yesNo(e.getSettledByOmbudsman()) : null)
                .complaintAgainstManagement(e != null ? yesNo(e.getComplaintAgainstManagement()) : null)
                .staffOfREEmployerRelationship(e != null ? yesNo(e.getStaffOfRe()) : null)
                .complaintFiledWithCEPCOrRBI(filedWithCepc)
                .disputeBetweenREs(e != null ? yesNo(e.getDisputeBetweenRes()) : null)
                .completeInformationUnavailable(e != null ? yesNo(e.getCompleteInformationUnavailable()) : null)
                .writtenComplaintFiledWithRE(e != null ? yesNo(e.getFiledWithRe()) : null)
                .firstFiledWithREDate(e != null ? e.getFirstFiledWithReDate() : null)
                .receivedReplyFromEntity(e != null ? yesNo(e.getReceivedReply()) : null)
                .replyDate(add != null ? add.getReplyDate() : null)
                .build();
    }

    private RbioComplaintSummaryResponse.EntityDetails entityDetails(Complaint c, ComplaintRbioFormData fd) {
        RegulatedEntity re = c.getRegulatedEntityId() != null
                ? regulatedEntityRepository.findById(c.getRegulatedEntityId()).orElse(null)
                : null;
        String masterCategory = re != null ? re.getEntityType() : null;
        String storedModule = fd != null ? fd.getModuleName() : null;

        return RbioComplaintSummaryResponse.EntityDetails.builder()
                .id(c.getRegulatedEntityId())
                .entityName(c.getEntityName())
                // Both are stored per complaint but derivable from the entity, so the master is the
                // fallback: complaints filed before the entity picker wrote them would otherwise show these
                // fields blank even though the entity they point at determines them.
                .moduleName(storedModule != null ? storedModule : RegulatedEntity.moduleNameFor(masterCategory))
                .entityCategory(c.getEntityCategory() != null
                        ? c.getEntityCategory()
                        : RegulatedEntity.entityCategoryFor(masterCategory))
                // Belongs to the entity and not to the complaint, so it is read back from the master and
                // never persisted from the payload. Falls back to the master's category wording because
                // nothing populates the NBFC sub-classification for a bank.
                .entityType(re != null
                        ? RegulatedEntity.entityTypeDisplayFor(re.getEntityType(), re.getEntityTypeDetail())
                        : null)
                .bsrCode(c.getEntityBsrCode())
                .pincode(c.getEntityPincode())
                .country(fd != null ? fd.getEntityCountry() : null)
                .state(c.getEntityState())
                .district(c.getEntityDistrict())
                .city(c.getEntityCity())
                .branchName(c.getEntityBranchName())
                .branchCategory(c.getEntityBranchCategory())
                .branchCenterName(fd != null ? fd.getBranchCenterName() : null)
                .entityAddress(c.getEntityAddress())
                .build();
    }

    private RbioComplaintSummaryResponse.ComplainDetails complainDetails(
            Complaint c, ComplaintEligibilityAnswer e, ComplaintAdditionalDetail add,
            ComplaintRbioFormData fd) {
        Long fdId = fd != null ? fd.getId() : null;
        LocalDate dateOfFiling = fd != null ? fd.getDateOfFilingComplaint() : null;

        return RbioComplaintSummaryResponse.ComplainDetails.builder()
                .basicIdentificationDto(RbioComplaintSummaryResponse.BasicIdentification.builder()
                        .emailId(c.getComplainantEmail())
                        .entityName(c.getEntityName())
                        .otherEntityName(fd != null ? fd.getOtherEntityName() : null)
                        .registrationWithRbiDate(fd != null ? fd.getRegistrationWithRbiDate() : null)
                        .build())
                .complaintClassification(RbioComplaintSummaryResponse.ComplaintClassification.builder()
                        .id(c.getCategoryId())
                        .complaintCategory(categoryName(c))
                        .complaintSubCategory1(add != null ? add.getSubCategory1() : null)
                        .complaintSubCategory2(add != null ? add.getSubCategory2() : null)
                        .complaintRegistrationDateValid(
                                fd != null ? yesNo(fd.getComplaintRegistrationDateValid()) : null)
                        .dateOfFilingComplaint(dateOfFiling)
                        .build())
                .financialDetails(RbioComplaintSummaryResponse.FinancialDetails.builder()
                        .id(add != null ? add.getId() : null)
                        .reminderSent(e != null ? yesNo(e.getSentReminder()) : null)
                        .disputedAmount(c.getAmountInvolved())
                        .compensationSought(add != null ? add.getCompensationSought() : null)
                        .dateOfFiling(dateOfFiling)
                        .build())
                .legalCaseDetails(RbioComplaintSummaryResponse.LegalCaseDetails.builder()
                        .id(fdId)
                        .legalCaseFiled(fd != null ? yesNo(fd.getLegalCaseFiled()) : null)
                        .filingDate(fd != null ? fd.getLegalFilingDate() : null)
                        .preEnquiryReceived(fd != null ? yesNo(fd.getPreEnquiryReceived()) : null)
                        .highPriorityComplaint(fd != null ? yesNo(fd.getHighPriorityComplaint()) : null)
                        .loanDisposalAmount(fd != null ? fd.getLoanDisposalAmount() : null)
                        .build())
                .additionalInformation(RbioComplaintSummaryResponse.AdditionalInformation.builder()
                        .id(fdId)
                        .comments(fd != null ? fd.getAdditionalComments() : null)
                        .crpcProposedAction(c.getProposedAction())
                        .vernacularLanguage(fd != null ? fd.getVernacularLanguage() : null)
                        .dateOfFiling(dateOfFiling)
                        .build())
                .flagsAndIndicators(RbioComplaintSummaryResponse.FlagsAndIndicators.builder()
                        .id(fdId)
                        .complaintRegardingPension(fd != null ? yesNo(fd.getComplaintRegardingPension()) : null)
                        .complaintAgainstBusinessCorrespondent(
                                add != null ? yesNo(add.getIsBusinessCorrespondent()) : null)
                        .atmCreditDebitCard(fd != null ? yesNo(fd.getAtmCreditDebitCard()) : null)
                        .schemeFlag(fd != null ? fd.getSchemeFlag() : null)
                        .rboCgpcOld(fd != null ? fd.getRboCgpcOld() : null)
                        .groundsFlag(fd != null ? fd.getGroundsFlag() : null)
                        .build())
                .complaintLinkage(RbioComplaintSummaryResponse.ComplaintLinkage.builder()
                        .id(fdId)
                        .freeMarkedComplaint(fd != null ? yesNo(fd.getFreeMarkedComplaint()) : null)
                        .currentComplaintNumber(c.getComplaintNumber())
                        .replyWithin30Days(fd != null ? replyLabel(fd.getReplyWithin30Days()) : null)
                        .build())
                .build();
    }

    private String categoryName(Complaint c) {
        if (c.getCategoryName() != null && !c.getCategoryName().isBlank()) return c.getCategoryName();
        if (c.getCategoryId() == null) return null;
        return categoryRepository.findById(c.getCategoryId()).map(cat -> cat.getName()).orElse(null);
    }

    // ---------------------------------------------------------------- write

    /**
     * Records that this officer has opened the complaint, which is what the dashboard grid styles its
     * rows by. Kept out of {@link #getSummary} so that read stays read-only, and a no-op once this
     * officer has already read it so reopening neither writes nor re-announces on every view.
     *
     * <p>Per officer, not per complaint: the grid must keep showing a complaint as unread to everyone
     * who has not opened it themselves, however many colleagues have.
     *
     * @return the complaint number when this call was the one that recorded the read, so the caller can
     *         publish it to the search index after the transaction commits; empty otherwise.
     */
    @Transactional
    @CacheEvict(value = "dashboard", allEntries = true)
    public Optional<String> markRead(Long complaintId, String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return complaintRepository.findById(complaintId)
                .filter(c -> !readReceiptRepository.existsByComplaintIdAndUsername(complaintId, username))
                .map(c -> {
                    readReceiptRepository.save(ComplaintReadReceipt.builder()
                            .complaintId(complaintId)
                            .username(username)
                            .readAt(LocalDateTime.now())
                            .build());
                    return c.getComplaintNumber();
                });
    }

    /**
     * Apply an officer's edits. Accepts the same nested shape {@link #getSummary} returns; a section
     * or field that is absent is left untouched, while a field present with a {@code null} value is
     * cleared. That distinction is why this takes a Map rather than a typed DTO.
     */
    @Transactional
    @CacheEvict(value = "dashboard", allEntries = true)
    public RbioComplaintSummaryResponse updateSummary(Long complaintId, Map<String, Object> payload, String actor,
                                                     Collection<String> actorRoles) {
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        if (!rbioHierarchyService.canEdit(c.getAssignedOfficer(), actor, actorRoles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Complaint " + c.getComplaintNumber()
                    + " is assigned to " + c.getAssignedOfficer()
                    + "; only that officer or RBIO_ADMIN may edit it. You have view access.");
        }

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
        // Last, so the name it derives wins over any entityName sent in the same payload.
        setIfPresent(entity, "id", v -> applyRegulatedEntity(c, longVal(v, "entityDetails.id")));

        setIfPresent(classification, "complaintCategory", v -> c.setCategoryName(str(v, "complaintCategory")));
        setIfPresent(financial, "disputedAmount", v -> c.setAmountInvolved(decimal(v, "disputedAmount")));
        setIfPresent(additional, "crpcProposedAction", v -> c.setProposedAction(str(v, "crpcProposedAction")));
    }

    /**
     * Moves the complaint to a different regulated entity, taking the name from the entity record
     * rather than from the request.
     *
     * <p>The id is what the rest of the application acts on — {@code entityDetails.entityType} is read
     * back through it, and NodalOfficerRecordService resolves the forwarding address by it first — so
     * an id and a name that disagree mean the screen shows one entity while the complaint is forwarded
     * to another. Deriving the name here makes that impossible regardless of what the caller sent.
     */
    private void applyRegulatedEntity(Complaint c, Long entityId) {
        if (entityId == null) {
            c.setRegulatedEntityId(null);
            return;
        }

        RegulatedEntity entity = regulatedEntityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Field 'entityDetails.id' does not match any regulated entity: " + entityId));

        c.setRegulatedEntityId(entity.getId());
        c.setEntityName(entity.getName());
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
        setIfPresent(e, "staffOfREEmployerRelationship",
                v -> row.setStaffOfRe(toYesNo(v, "staffOfREEmployerRelationship")));
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

        setIfPresent(additional, "comments", v -> row.setAdditionalComments(str(v, "comments")));
        setIfPresent(additional, "vernacularLanguage", v -> row.setVernacularLanguage(str(v, "vernacularLanguage")));

        setIfPresent(flags, "complaintRegardingPension",
                v -> row.setComplaintRegardingPension(toYesNo(v, "complaintRegardingPension")));
        setIfPresent(flags, "atmCreditDebitCard", v -> row.setAtmCreditDebitCard(toYesNo(v, "atmCreditDebitCard")));
        setIfPresent(flags, "schemeFlag", v -> row.setSchemeFlag(str(v, "schemeFlag")));
        setIfPresent(flags, "rboCgpcOld", v -> row.setRboCgpcOld(str(v, "rboCgpcOld")));
        setIfPresent(flags, "groundsFlag", v -> row.setGroundsFlag(str(v, "groundsFlag")));

        setIfPresent(linkage, "freeMarkedComplaint",
                v -> row.setFreeMarkedComplaint(toYesNo(v, "freeMarkedComplaint")));
        setIfPresent(linkage, "replyWithin30Days",
                v -> row.setReplyWithin30Days(replyToken(v)));

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
        row.setAdditionalComments(draft.getAdditionalComments());
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
        row.setReplyWithin30Days(replyToken(draft.getReceivedReplyWithin30Days()));

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

    /**
     * Tri-state reply answer -> stored token. Lenient like {@link #looseYesNo}, because the same helper
     * normalises both the PUT payload and the free-text {@code EmailDraft} value at backfill, and an
     * unrecognised draft value must not fail the conversion.
     */
    private static String replyToken(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b ? "YES" : "NO";
        String s = String.valueOf(v).trim().replace(' ', '_');
        if ("yes".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s)) return "YES";
        if ("no".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) return "NO";
        if ("not_applicable".equalsIgnoreCase(s) || "na".equalsIgnoreCase(s)) return "NOT_APPLICABLE";
        return null;
    }

    /** Stored reply token -> the label the RBIO summary form renders. */
    private static String replyLabel(String v) {
        String token = replyToken(v);
        if (token == null) return null;
        return switch (token) {
            case "YES" -> "Yes";
            case "NO" -> "No";
            default -> "Not Applicable";
        };
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
