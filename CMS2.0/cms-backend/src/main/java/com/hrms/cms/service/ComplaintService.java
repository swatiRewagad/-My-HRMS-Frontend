package com.hrms.cms.service;

import com.hrms.cms.dto.*;
import com.hrms.cms.entity.*;
import com.hrms.cms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final BankRepository bankRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final ComplaintAttachmentRepository attachmentRepository;
    private final ComplaintCreationFinalizer creationFinalizer;
    private final ComplaintRoutingService routingService;
    private final ComplaintNumberGeneratorService complaintNumberGenerator;
    private final ComplaintCommentRepository complaintCommentRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;
    private final ComplaintDraftRepository complaintDraftRepository;
    private final ComplaintEligibilityAnswerRepository eligibilityAnswerRepository;
    private final ComplaintAdditionalDetailRepository additionalDetailRepository;
    private final ComplaintRepresentativeRepository representativeRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(value = "dashboard", unless = "#result == null")
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        return DashboardResponse.builder()
                .totalComplaints(complaintRepository.count())
                .pendingComplaints(complaintRepository.countByStatus("pending"))
                .inProgressComplaints(complaintRepository.countByStatus("in_progress"))
                .resolvedComplaints(complaintRepository.countByStatus("resolved"))
                .closedComplaints(complaintRepository.countByStatus("closed"))
                .escalatedComplaints(complaintRepository.countByStatus("escalated"))
                .highPriority(complaintRepository.countByPriority("high"))
                .mediumPriority(complaintRepository.countByPriority("medium"))
                .lowPriority(complaintRepository.countByPriority("low"))
                .build();
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaints() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<Complaint> getAllComplaintsPaged(Pageable pageable) {
        return complaintRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Complaint> getByStatusPaged(String status, Pageable pageable) {
        return complaintRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Complaint> searchComplaintsPaged(String query, Pageable pageable) {
        return complaintRepository.searchPaged(query, pageable);
    }

    @Transactional(readOnly = true)
    public Complaint getComplaint(Long id) {
        return complaintRepository.findById(id).orElseThrow(() -> new RuntimeException("Complaint not found"));
    }

    @Transactional(readOnly = true)
    public Complaint getByComplaintNumber(String number) {
        return complaintRepository.findByComplaintNumber(number).orElseThrow(() -> new RuntimeException("Complaint not found"));
    }

    @Transactional(readOnly = true)
    public List<Complaint> getByComplainantPhone(String phone) {
        return complaintRepository.findByComplainantPhoneOrderByCreatedAtDesc(phone);
    }

    @Transactional(readOnly = true)
    public List<Complaint> getByStatus(String status) {
        return complaintRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public List<Complaint> searchComplaints(String query) {
        return complaintRepository.search(query);
    }

    @CacheEvict(value = "dashboard", allEntries = true)
    @Transactional
    public Complaint fileComplaint(FileComplaintRequest req) {
        validatePriorReComplaintFields(req);

        // The portal deletes the draft immediately after this call returns, so the wizard state
        // has to be copied across now. Prefer the payload; fall back to the surviving draft row.
        ComplaintDraft draft = resolveDraft(req);
        Map<String, Object> formData = req.getFormData() != null
                ? req.getFormData()
                : parseFormData(draft != null ? draft.getFormDataJson() : null);
        Map<String, String> answers = req.getEligibilityAnswers() != null
                ? req.getEligibilityAnswers()
                : parseAnswers(draft != null ? draft.getEligibilityAnswersJson() : null);
        String draftId = req.getDraftId() != null ? req.getDraftId() : (draft != null ? draft.getDraftId() : null);

        // Determine department (RBIO/CEPC) based on RE scheme coverage
        String entityCode = "";
        if (req.getEntityName() != null && !req.getEntityName().isBlank()) {
            entityCode = req.getEntityName();
        } else if (req.getRegulatedEntityId() != null) {
            entityCode = regulatedEntityRepository.findById(req.getRegulatedEntityId())
                    .map(RegulatedEntity::getName).orElse("");
        } else if (req.getBankId() != null) {
            entityCode = bankRepository.findById(req.getBankId())
                    .map(Bank::getCode).orElse("");
        }
        String department = routingService.resolveDepartment(entityCode);

        // Generate complaint number: N + FY + OfficeCode + Sequence
        // Vernacular/CRPC complaints don't impact threshold counter
        boolean isVernacularOrCrpc = "EMAIL".equals(req.getFilingType()) || "PHYSICAL_LETTER".equals(req.getFilingType());
        String complaintNumber = complaintNumberGenerator.generateComplaintNumber(
                department, req.getComplainantState(), req.getComplainantDistrict(), isVernacularOrCrpc);

        Complaint complaint = Complaint.builder()
                .complaintNumber(complaintNumber)
                .complainantName(req.getComplainantName())
                .complainantEmail(req.getComplainantEmail())
                .complainantPhone(req.getComplainantPhone())
                .complainantAddress(req.getComplainantAddress())
                .complainantState(req.getComplainantState())
                .complainantDistrict(req.getComplainantDistrict())
                .complainantPincode(firstNonBlank(req.getComplainantPincode(), str(formData, "pincode", 20)))
                .entityState(firstNonBlank(req.getEntityState(), str(formData, "entityState", 100)))
                .entityDistrict(firstNonBlank(req.getEntityDistrict(), str(formData, "entityDistrict", 100)))
                .entityPincode(firstNonBlank(req.getEntityPincode(), str(formData, "entityPincode", 20)))
                .entityBranchName(firstNonBlank(req.getEntityBranchName(), str(formData, "entityBranch", 300)))
                .bankId(req.getBankId())
                .regulatedEntityId(req.getRegulatedEntityId())
                .bankBranch(req.getBankBranch())
                .accountNumber(req.getAccountNumber())
                .amountInvolved(req.getAmountInvolved())
                .categoryId(req.getCategoryId())
                .subject(req.getSubject())
                .description(req.getDescription())
                .reliefSought(firstNonBlank(req.getReliefSought(), str(formData, "reliefSought", 2000)))
                .priority(req.getPriority())
                .filingType(req.getFilingType())
                .bankComplaintReference(req.getBankComplaintReference())
                .bankComplaintDate(req.getBankComplaintDate() != null ? LocalDateTime.parse(req.getBankComplaintDate() + "T00:00:00") : null)
                .priorReComplaint(req.getPriorReComplaint())
                .reComplaintDate(req.getReComplaintDate())
                .reComplaintReference(req.getReComplaintReference())
                .reRepliedAndDissatisfied(req.getReRepliedAndDissatisfied())
                .build();

        complaint.setEntityCode(entityCode);
        complaint.setEntityName(req.getEntityName());
        if (req.getCategoryId() != null) {
            categoryRepository.findById(req.getCategoryId())
                    .ifPresent(cat -> complaint.setCategoryName(cat.getName()));
        }
        if (complaint.getCategoryName() == null && req.getCategoryName() != null) {
            complaint.setCategoryName(req.getCategoryName());
        }
        // Before routing: the resolved office code is what scopes assignment to that office's officers.
        ComplaintOfficeResolutionService.OfficeResolution office =
                creationFinalizer.applyOffice(complaint, department);

        ComplaintRoutingService.RoutingDecision routing =
                routingService.routeComplaint(complaint, entityCode, office.officeCode());
        complaint.setDepartment(routing.getDepartment());
        complaint.setAssignedRole(routing.getAssignedRole());
        complaint.setAssignedOfficer(routing.getAssignedOfficer());
        complaint.setWorkflowStage(routing.getStage());

        Complaint saved = complaintRepository.save(complaint);

        persistEligibilityAnswers(saved.getId(), draftId, answers);
        persistAdditionalDetails(saved.getId(), draftId, formData);
        persistRepresentative(saved.getId(), draftId, formData);

        addTimeline(saved.getId(), "filed", "System",
                "Complaint filed and routed to " + routing.getDepartment()
                        + " / " + office.officeName() + " (" + routing.getReason() + ")",
                null, "pending");

        creationFinalizer.afterSave(saved);

        return saved;
    }

    private ComplaintDraft resolveDraft(FileComplaintRequest req) {
        if (req.getFormData() != null && req.getEligibilityAnswers() != null) {
            return null;
        }
        if (req.getDraftId() != null && !req.getDraftId().isBlank()) {
            ComplaintDraft byId = complaintDraftRepository.findByDraftId(req.getDraftId()).orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        // Drafts are upserted one-per-phone, so the phone alone identifies the draft.
        if (req.getComplainantPhone() != null && !req.getComplainantPhone().isBlank()) {
            return complaintDraftRepository.findByPhoneOrderByUpdatedAtDesc(req.getComplainantPhone())
                    .stream().findFirst().orElse(null);
        }
        return null;
    }

    private void persistEligibilityAnswers(Long complaintId, String draftId, Map<String, String> a) {
        if (a == null || a.isEmpty()) {
            return;
        }
        eligibilityAnswerRepository.save(ComplaintEligibilityAnswer.builder()
                .complaintId(complaintId)
                .draftId(cap(draftId, 50))
                .regulatedEntityId(longOrNull(a.get("regulatedEntity")))
                .filedWithRe(ans(a, "filedWithRE"))
                .receivedReply(ans(a, "receivedReply"))
                .sentReminder(ans(a, "sentReminder"))
                .isSubJudice(ans(a, "isSubJudice"))
                .alreadySettled(ans(a, "alreadySettled"))
                .throughAdvocate(ans(a, "throughAdvocateEligibility"))
                .pendingBeforeOmbudsman(ans(a, "pendingBeforeOmbudsman"))
                .settledByOmbudsman(ans(a, "settledByOmbudsman"))
                .staffOfRe(ans(a, "staffOfRE"))
                .previouslyFiledWithCepc(ans(a, "previouslyFiledWithCEPC"))
                .employeeOfRe(ans(a, "employeeOfRE"))
                .employerRelationship(ans(a, "employerRelationship"))
                .build());
    }

    private void persistAdditionalDetails(Long complaintId, String draftId, Map<String, Object> f) {
        if (f == null || f.isEmpty()) {
            return;
        }
        ComplaintAdditionalDetail d = ComplaintAdditionalDetail.builder()
                .complaintId(complaintId)
                .draftId(cap(draftId, 50))
                .age(intOrNull(f, "age"))
                .gender(str(f, "gender", 20))
                .complainantCategory(str(f, "complainantCategory", 50))
                .isComplainantSelf(str(f, "isComplainantSelf", 10))
                .organizationName(str(f, "organizationName", 300))
                .orgLandline(str(f, "orgLandline", 30))
                .hasAccountWithRe(str(f, "hasAccountWithRE", 10))
                .accountType(str(f, "accountType", 200))
                .savingsAccountNumber(str(f, "savingsAccountNumber", 50))
                .atmDebitCardNumber(str(f, "atmDebitCardNumber", 50))
                .cardNumber(str(f, "cardNumber", 50))
                .creditCardNumber(str(f, "creditCardNumber", 50))
                .isCreditCardComplaint(str(f, "isCreditCardComplaint", 10))
                .loanAccountNumber(str(f, "loanAccountNumber", 50))
                .isWalletComplaint(str(f, "isWalletComplaint", 10))
                .walletName(str(f, "walletName", 200))
                .isBusinessCorrespondent(str(f, "isBusinessCorrespondent", 10))
                .transactionRefNumber(str(f, "transactionRefNumber", 100))
                .disputeDate(dateOrNull(f, "disputeDate"))
                .compensationSought(moneyOrNull(f, "compensationSought"))
                .receivedReplyFromEntity(str(f, "receivedReplyFromEntity", 10))
                .replyDate(dateOrNull(f, "replyDate"))
                .reminderDate(dateOrNull(f, "reminderDate"))
                .subCategory1(str(f, "subCategory1", 200))
                .subCategory2(str(f, "subCategory2", 200))
                .build();
        additionalDetailRepository.save(d);
    }

    private void persistRepresentative(Long complaintId, String draftId, Map<String, Object> f) {
        if (f == null || f.isEmpty()) {
            return;
        }
        String repName = str(f, "repName", 200);
        boolean declared = "yes".equalsIgnoreCase(str(f, "hasAuthRep", 10))
                || "yes".equalsIgnoreCase(str(f, "authorizeRepresentative", 10));
        if (!declared && repName == null) {
            return;
        }
        representativeRepository.save(ComplaintRepresentative.builder()
                .complaintId(complaintId)
                .draftId(cap(draftId, 50))
                .hasAuthRep(str(f, "hasAuthRep", 10))
                .authorizeRepresentative(str(f, "authorizeRepresentative", 10))
                .throughAdvocate(str(f, "throughAdvocate", 10))
                .repName(repName)
                .repPhone(str(f, "repPhone", 20))
                .repEmail(str(f, "repEmail", 254))
                .repAddress(str(f, "repAddress", 500))
                .repCity(str(f, "repCity", 100))
                .repDistrict(str(f, "repDistrict", 100))
                .repState(str(f, "repState", 100))
                .repPincode(str(f, "repPincode", 20))
                .build());
    }

    private Map<String, Object> parseFormData(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, String> parseAnswers(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static String ans(Map<String, String> a, String key) {
        String v = a.get(key);
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : cap(t, 10);
    }

    /**
     * The wizard emits empty strings for every skipped question, and these values are never
     * bean-validated, so each accessor below returns null rather than letting a blank or an
     * unparseable value roll back the whole filing.
     */
    private static String str(Map<String, Object> f, String key, int maxLength) {
        if (f == null) {
            return null;
        }
        Object v = f.get(key);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : cap(s, maxLength);
    }

    private static String cap(String s, int maxLength) {
        return s == null || s.length() <= maxLength ? s : s.substring(0, maxLength);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static Integer intOrNull(Map<String, Object> f, String key) {
        String s = str(f, key, 20);
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate dateOrNull(Map<String, Object> f, String key) {
        String s = str(f, key, 30);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal moneyOrNull(Map<String, Object> f, String key) {
        String s = str(f, key, 40);
        if (s == null) {
            return null;
        }
        try {
            // Amounts arrive Indian-grouped from the wizard, e.g. "9,89,90,900".
            return new BigDecimal(s.replace(",", "").replace("₹", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @CacheEvict(value = "dashboard", allEntries = true)
    @Transactional
    public Complaint updateComplaint(Long id, UpdateComplaintRequest req) {
        Complaint complaint = getComplaint(id);
        String oldStatus = complaint.getStatus();

        if (req.getStatus() != null) {
            complaint.setStatus(req.getStatus());
            if ("resolved".equals(req.getStatus())) complaint.setResolvedAt(LocalDateTime.now());
            if ("closed".equals(req.getStatus())) complaint.setClosedAt(LocalDateTime.now());
            if ("escalated".equals(req.getStatus())) complaint.setEscalatedAt(LocalDateTime.now());
        }
        if (req.getPriority() != null) complaint.setPriority(req.getPriority());
        if (req.getAssignedOfficer() != null) complaint.setAssignedOfficer(req.getAssignedOfficer());

        Complaint saved = complaintRepository.save(complaint);

        String action = req.getStatus() != null ? "status_change" : "update";
        addTimelineAsync(id, action, req.getAssignedOfficer() != null ? req.getAssignedOfficer() : "System",
                req.getRemarks(), oldStatus, complaint.getStatus());

        return saved;
    }

    @Transactional
    public Complaint updateComplaintDirectly(Complaint complaint) {
        return complaintRepository.save(complaint);
    }

    @Transactional
    public void deleteComplaint(Long id) {
        complaintRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ComplaintTimeline> getTimeline(Long complaintId) {
        return timelineRepository.findByComplaintIdOrderByPerformedAtDesc(complaintId);
    }

    @Async("taskExecutor")
    @Transactional
    public void addTimelineAsync(Long complaintId, String action, String performedBy, String remarks, String fromStatus, String toStatus) {
        ComplaintTimeline entry = ComplaintTimeline.builder()
                .complaintId(complaintId)
                .action(action)
                .performedBy(performedBy)
                .remarks(remarks)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .build();
        timelineRepository.save(entry);
    }

    public void addTimeline(Long complaintId, String action, String performedBy, String remarks, String fromStatus, String toStatus) {
        ComplaintTimeline entry = ComplaintTimeline.builder()
                .complaintId(complaintId)
                .action(action)
                .performedBy(performedBy)
                .remarks(remarks)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .build();
        timelineRepository.save(entry);
    }

    @Cacheable(value = "categories", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<ComplaintCategory> getAllCategories() {
        return categoryRepository.findByStatus("active");
    }

    @Cacheable(value = "categories-root", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<ComplaintCategory> getRootCategories() {
        return categoryRepository.findByParentIdIsNullOrderBySortOrder();
    }

    @Cacheable(value = "categories-sub", key = "#parentId", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<ComplaintCategory> getSubCategories(Long parentId) {
        return categoryRepository.findByParentIdOrderBySortOrder(parentId);
    }

    @Cacheable(value = "banks", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<Bank> getAllBanks() {
        return bankRepository.findByStatus("active");
    }

    @Cacheable(value = "banks-by-type", key = "#type", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<Bank> getBanksByType(String type) {
        return bankRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<ComplaintAttachment> getAttachments(Long complaintId) {
        return attachmentRepository.findByComplaintId(complaintId);
    }

    @Transactional
    public ComplaintAttachment saveAttachment(ComplaintAttachment attachment) {
        return attachmentRepository.save(attachment);
    }

    @CacheEvict(value = "dashboard", allEntries = true)
    @Transactional
    public Complaint updateMaintainability(Complaint complaint) {
        Complaint saved = complaintRepository.save(complaint);
        addTimelineAsync(saved.getId(), "maintainability_decision", complaint.getMaintainabilityDeterminedBy(),
                "Determination: " + complaint.getMaintainabilityDetermination(),
                null, complaint.getStatus());
        return saved;
    }

    private void validatePriorReComplaintFields(FileComplaintRequest req) {
        if (Boolean.TRUE.equals(req.getPriorReComplaint())) {
            if (req.getReComplaintDate() == null) {
                throw new IllegalArgumentException("RE complaint date is required when prior complaint to RE is indicated");
            }
            if (req.getReComplaintReference() == null || req.getReComplaintReference().isBlank()) {
                throw new IllegalArgumentException("RE complaint reference is required when prior complaint to RE is indicated");
            }
            if (req.getReComplaintDate().isAfter(java.time.LocalDate.now())) {
                throw new IllegalArgumentException("RE complaint date cannot be in the future");
            }
        }
    }

    /**
     * Paged full-table walk feeding the search service's reindex.
     *
     * <p>Sorted by primary key rather than a timestamp: the sort must be unique and monotonic, or rows
     * inserted while the reindexer is paging shift later pages and some complaints are visited twice
     * while others are skipped entirely. {@code createdAt} is neither unique nor stable under
     * concurrent writes.
     */
    @Transactional(readOnly = true)
    public Page<Complaint> getStreamedComplaints(int page, int size) {
        return complaintRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
        );
    }

    @Transactional(readOnly = true)
    public List<ComplaintComment> getCommentsByComplaintNumber(String complaintNumber) {
        if (complaintNumber == null || complaintNumber.isBlank()) {
            throw new IllegalArgumentException("Complaint number cannot be empty");
        }
        return complaintCommentRepository.findByComplaintNumberOrderByCreatedAtDesc(complaintNumber);
    }

}
