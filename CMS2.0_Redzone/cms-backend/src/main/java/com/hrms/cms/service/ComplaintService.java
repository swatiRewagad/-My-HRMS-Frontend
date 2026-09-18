package com.hrms.cms.service;

import com.hrms.cms.dto.*;
import com.hrms.cms.entity.*;
import com.hrms.cms.event.ComplaintEventPublisher;
import com.hrms.cms.repository.*;
import com.hrms.cms.service.mre.MreEntityCoverageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final BankRepository bankRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final ComplaintAttachmentRepository attachmentRepository;
    private final ComplaintEventPublisher eventPublisher;
    private final ComplaintRoutingService routingService;
    private final ComplaintNumberGeneratorService complaintNumberGenerator;
    private final OfficeRoutingService officeRoutingService;
    private final NodalOfficerRecordService nodalOfficerRecordService;
    private final OfficeAssignmentStrategyService officeAssignmentStrategyService;

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

        // Determine department (RBIO/CEPC) based on RE scheme coverage
        String entityCode = "";
        if (req.getEntityName() != null && !req.getEntityName().isBlank()) {
            entityCode = req.getEntityName();
        } else if (req.getBankId() != null) {
            entityCode = bankRepository.findById(req.getBankId())
                    .map(Bank::getCode).orElse("");
        }
        // UST473: an entity outside the Scheme belongs to CEPC, not RBIO. The determination is captured
        // rather than reduced to a department string, because the story requires the CHECK RESULT to be
        // recorded on the complaint and because an AMBIGUOUS or UNKNOWN entity must be visible as such —
        // both route to CEPC, but for a reason a human has to confirm rather than because CEPC was chosen.
        MreEntityCoverageService.Coverage coverage = routingService.resolveCoverage(entityCode);
        String department = coverage.department();

        // Generate complaint number: N + FY + OfficeCode + Sequence.
        // The resolved office is captured rather than discarded, so it can be persisted on the
        // complaint below. Previously it lived only inside the number string, leaving
        // COMPLAINTS.rbio_office_code NULL on every complaint filed after the V36 backfill.
        ComplaintNumberGeneratorService.NumberedComplaint numbered =
                complaintNumberGenerator.generateForOffice(
                        department, req.getComplainantState(), req.getComplainantDistrict());
        String complaintNumber = numbered.complaintNumber();

        Complaint complaint = Complaint.builder()
                .complaintNumber(complaintNumber)
                .complainantName(req.getComplainantName())
                .complainantEmail(req.getComplainantEmail())
                .complainantPhone(req.getComplainantPhone())
                .complainantAddress(req.getComplainantAddress())
                .complainantState(req.getComplainantState())
                .complainantDistrict(req.getComplainantDistrict())
                .bankId(req.getBankId())
                .bankBranch(req.getBankBranch())
                .accountNumber(req.getAccountNumber())
                .categoryId(req.getCategoryId())
                .subject(req.getSubject())
                .description(req.getDescription())
                .reliefSought(req.getReliefSought())
                .priority(req.getPriority())
                .filingType(req.getFilingType())
                .bankComplaintReference(req.getBankComplaintReference())
                .bankComplaintDate(req.getBankComplaintDate() != null ? LocalDateTime.parse(req.getBankComplaintDate() + "T00:00:00") : null)
                .priorReComplaint(req.getPriorReComplaint())
                .reComplaintDate(req.getReComplaintDate())
                .reComplaintReference(req.getReComplaintReference())
                .reRepliedAndDissatisfied(req.getReRepliedAndDissatisfied())
                .declarationAccepted(req.getDeclarationAccepted())
                .hasAuthRep(req.getHasAuthRep())
                .throughAdvocate(req.getThroughAdvocate())
                .repName(req.getRepName())
                .repPhone(req.getRepPhone())
                .repEmail(req.getRepEmail())
                .repAddress(req.getRepAddress())
                .repState(req.getRepState())
                .repDistrict(req.getRepDistrict())
                .repCity(req.getRepCity())
                .repPincode(req.getRepPincode())
                .build();

        complaint.setEntityCode(entityCode);

        // UST473: record the determination and the Scheme it was made under. schemeVersion had no writer
        // anywhere before this — it was a column every reader fell back off — so a complaint could not say
        // which Scheme's rules had been applied to it.
        complaint.setSchemeCoverageStatus(coverage.status().name());
        complaint.setSchemeCoverageReason(coverage.reason());
        complaint.setSchemeVersion(coverage.schemeVersion());

        ComplaintRoutingService.RoutingDecision routing = routingService.routeComplaint(complaint, entityCode);
        complaint.setDepartment(routing.getDepartment());
        complaint.setAssignedRole(routing.getAssignedRole());
        complaint.setAssignedOfficer(routing.getAssignedOfficer());
        complaint.setWorkflowStage(routing.getStage());

        // UST464-467/UST755: claim capacity at the territorially-correct office and record the office
        // that actually accepted the complaint. routeToOffice may divert to the configured overflow
        // office, in which case the complaint belongs to THAT office and rbio_office_code must say so
        // — the complaint number keeps the originally-numbered office, which is why the two can differ.
        Map<String, Object> officeRouting = officeRoutingService.routeToOffice(numbered.officeCode(), false);
        String officeStatus = String.valueOf(officeRouting.get("status"));
        String acceptingOffice = String.valueOf(officeRouting.getOrDefault("officeId", ""));

        if (OfficeRoutingService.STATUS_NOT_FOUND.equals(officeStatus)) {
            // Do not invent an office: an unconfigured office means the capacity table and the office
            // masters disagree, and a guessed territorial jurisdiction decides which Ombudsman may
            // lawfully hear the complaint and any appeal. Record the numbered office and surface the
            // misconfiguration loudly instead of silently placing the case elsewhere.
            log.error("Office '{}' has no active capacity configuration — complaint {} recorded against it "
                            + "without capacity accounting. OFFICE_THRESHOLD_CONFIG needs a row for this office.",
                    numbered.officeCode(), complaintNumber);
            complaint.setRbioOfficeCode(numbered.officeCode());
        } else {
            complaint.setRbioOfficeCode(acceptingOffice.isBlank() ? numbered.officeCode() : acceptingOffice);
        }

        // UST468-472: the office that ACCEPTED the complaint decides how its Dealing Officer is chosen —
        // rotation, entity mapping, or category mapping. This runs after office routing because an overflow
        // diversion changes which office's configuration applies, and it overrides the generic officer
        // routeComplaint picked, which knows nothing about per-office policy.
        //
        // Only RBIO complaints are re-resolved. CRPC intake assigns a DEO by its own rota and CEPC has its
        // own ladder, so re-deciding those here would silently take over two workflows this story does not
        // cover.
        OfficeAssignmentStrategyService.Resolution officerChoice = null;
        if ("RBIO".equals(routing.getDepartment()) && complaint.getRbioOfficeCode() != null) {
            officerChoice = officeAssignmentStrategyService.resolveOfficer(
                    complaint.getRbioOfficeCode(), entityCode, req.getCategoryId());
            if (officerChoice.isAssigned()) {
                complaint.setAssignedOfficer(officerChoice.officerId());
            }
        }

        Complaint saved = complaintRepository.save(complaint);

        addTimeline(saved.getId(), "filed", "System",
                "Complaint filed and routed to " + routing.getDepartment() + " (" + routing.getReason() + ")",
                null, "pending");

        // Traceability for the office decision (UST469/472 require the lookup to be recorded, and an
        // overflow diversion changes which office a citizen must deal with, so it cannot be log-only).
        if (OfficeRoutingService.STATUS_OVERFLOW.equals(officeStatus)
                || OfficeRoutingService.STATUS_AT_CAPACITY.equals(officeStatus)) {
            addTimeline(saved.getId(), "office_routing", "System",
                    "Office " + numbered.officeCode() + " (" + numbered.officeName() + ") at capacity: "
                            + officeStatus + "; complaint held at office " + saved.getRbioOfficeCode(),
                    null, null);
        }

        // UST473: an entity that could not be identified to ONE regulated entity is on the citizen's record
        // as needing confirmation. Only the review cases are written: a clean COVERED/NOT_COVERED
        // determination is already on the complaint's own columns, and repeating it here would bury the
        // cases that actually need somebody to look.
        if (coverage.needsReview()) {
            addTimeline(saved.getId(), "scheme_coverage_check", "System",
                    coverage.status() + ": " + coverage.reason()
                            + " Routed to " + department + " pending confirmation of the entity.",
                    null, null);
        }

        // UST469/UST472: the assignment lookup must be traceable per complaint, not only in a log file.
        // Recorded whenever the office ran a mapping strategy or fell back, because those are the cases
        // where somebody later asks "why did this land on an admin instead of the named officer?" — a
        // plain rotation needs no explanation and would only add noise to every timeline.
        if (officerChoice != null
                && !OfficeAssignmentStrategyService.Resolution.OUTCOME_ROTATED.equals(officerChoice.outcome())) {
            addTimeline(saved.getId(), "assignment_lookup", "System",
                    officerChoice.strategy() + " / " + officerChoice.outcome() + ": " + officerChoice.reason(),
                    null, null);
        }

        // UST569: the complaint now exists against an entity, so the Nodal Officer record it will be
        // answered through must exist too. Synchronous and inside this transaction, because a complaint
        // committed without its NO record is invisible to the staleness escalations that chase the entity
        // — nothing downstream would ever notice the omission.
        //
        // The office is re-resolved from the same jurisdiction inputs the complaint number used, so the
        // NO/PNO lookup is scoped to the office that will actually process the complaint (UST773).
        nodalOfficerRecordService.ensureRecordExists(
                saved.getId(), saved.getComplaintNumber(), entityCode, numbered.officeName());

        eventPublisher.publishComplaintIngested(saved);

        return saved;
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

    /**
     * Records a timeline event carrying the detail UST596 requires: the actor's role, a changed
     * field's old and new value, a closure clause, or a destination office.
     *
     * <p>THE WRITE CONTRACT for reassignment, closure and inter-office transfer events. Callers must
     * capture {@code oldValue} BEFORE mutating the complaint — the assignee is overwritten by the
     * transition before the timeline write, so reading it afterwards yields the new owner in both
     * fields and produces a reassignment record that appears to change nothing.
     *
     * <p>The plain {@link #addTimeline} overload stays for the many callers that record only a status
     * change; this one is additive rather than a widening of that signature, so no existing call site
     * silently starts writing nulls into the new columns.
     *
     * @param fieldName         what changed, e.g. assignedOfficer — null for events with no field change
     * @param oldValue          value before the change; must be read before mutation
     * @param newValue          value after the change
     * @param closureClause     Scheme clause for a closure event, recorded on the event because
     *                          Complaint.closureClause is mutable and a re-closure destroys it
     * @param destinationOffice receiving office for a routing or transfer event
     */
    @Transactional
    public void addDetailedTimeline(Long complaintId, String action, String performedBy,
                                    String performedByRole, String remarks,
                                    String fromStatus, String toStatus,
                                    String fieldName, String oldValue, String newValue,
                                    String closureClause, String destinationOffice,
                                    TimelineEventSource eventSource) {
        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(complaintId)
                .action(action)
                .performedBy(performedBy)
                .performedByRole(performedByRole)
                .remarks(remarks)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .closureClause(closureClause)
                .destinationOffice(destinationOffice)
                .eventSource(eventSource == null ? TimelineEventSource.MANUAL : eventSource)
                .build());
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
            // UST17: the acknowledgement number is optional — not every RE issues one, and requiring it
            // here rejected complaints the wizard had legitimately accepted.
            if (req.getReComplaintDate().isAfter(java.time.LocalDate.now())) {
                throw new IllegalArgumentException("RE complaint date cannot be in the future");
            }
        }
    }

}
