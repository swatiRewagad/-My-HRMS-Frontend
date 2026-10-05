package com.hrms.cms.service;

import com.hrms.cms.dto.*;
import com.hrms.cms.entity.*;
import com.hrms.cms.event.ComplaintEventPublisher;
import com.hrms.cms.exception.ComplaintNotEditableException;
import com.hrms.cms.exception.MandatoryFieldBlankException;
import com.hrms.cms.exception.RetentionPeriodActiveException;
import com.hrms.cms.repository.*;
import com.hrms.cms.security.RequestIdentity;
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
import java.util.Set;

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
    private final RetentionPolicyRepository retentionPolicyRepository;
    private final CepcAuditService cepcAuditService;
    private final AnomalyDetectionService anomalyDetectionService;

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
                .reReplyDate(req.getReReplyDate())
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

    /**
     * Statuses that settle a complaint. Once a record reaches one of these it is a closed matter, and
     * amending it in place would rewrite history rather than append to it: a reopen is a workflow
     * action with its own audit entry, not a field edit.
     */
    private static final Set<String> TERMINAL_STATUSES = Set.of("closed", "withdrawn", "rejected");

    @CacheEvict(value = "dashboard", allEntries = true)
    @Transactional
    public Complaint updateComplaint(Long id, UpdateComplaintRequest req, RequestIdentity editor) {
        Complaint complaint = getComplaint(id);
        String oldStatus = complaint.getStatus();

        if (oldStatus != null && TERMINAL_STATUSES.contains(oldStatus.toLowerCase())) {
            throw new ComplaintNotEditableException(complaint.getComplaintNumber(), oldStatus);
        }

        // A present-but-blank field is an attempt to clear a mandatory value, which is different from
        // omitting it. Only the latter means "leave this alone".
        rejectBlank(req.getStatus(), "status");
        rejectBlank(req.getPriority(), "priority");

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
        addTimelineAsync(id, action, editor.getUserId(), editor.getPrimaryRole(),
                req.getRemarks(), oldStatus, complaint.getStatus());

        return saved;
    }

    private void rejectBlank(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new MandatoryFieldBlankException(field);
        }
    }

    /** The policy that declares how long a complaint record is kept. Seeded by V17. */
    private static final String COMPLAINT_RETENTION_CATEGORY = "COMPLAINT_PII";

    /**
     * Fallback period, in days, used only if the COMPLAINT_PII policy row is missing.
     *
     * Seven years, matching every row V17 seeds. Fails CLOSED on purpose: a missing policy must not
     * read as "no retention applies", which would make deleting the row easier than keeping it.
     */
    private static final int DEFAULT_COMPLAINT_RETENTION_DAYS = 2555;

    /**
     * Permanently destroys a complaint — refused while the record is inside its retention period.
     *
     * WHY THIS CHANGED: the entire body used to be {@code complaintRepository.deleteById(id)}. There
     * was no retention check and no audit write, so any staff caller could irreversibly erase a
     * complaint one day into a seven-year statutory retention period, and the record and the
     * evidence that it had been destroyed disappeared together. The retention obligation was declared
     * in RETENTION_POLICY and consulted by the nightly sweep, while this endpoint — the only way to
     * destroy a single named record on demand — ignored it entirely.
     *
     * The period comes from the RETENTION_POLICY row, not a constant here, so an administrator
     * changing the declared period changes what this endpoint permits. The row's ENABLED flag is
     * deliberately NOT consulted: that flag governs whether the sweep may PURGE, and a disabled
     * policy still declares the obligation. Reading it would mean the record becomes freely
     * deletable exactly while automatic purging is switched off.
     *
     * Measured from closure. A complaint that is not yet closed has not started its retention clock
     * and is refused outright — deleting a live complaint is never a retention-expiry case.
     *
     * Every ATTEMPT is audited, permitted or refused, before the outcome is known. An attempt to
     * destroy a record inside its retention period is itself the event an investigator needs to
     * find, and auditing only successes would hide precisely the interesting ones.
     */
    @Transactional
    public void deleteComplaint(Long id) {
        Complaint complaint = complaintRepository.findById(id).orElse(null);
        if (complaint == null) {
            // Nothing to destroy and nothing to attribute the attempt to. Idempotent, as before.
            return;
        }

        int retentionDays = retentionPolicyRepository.findByCategory(COMPLAINT_RETENTION_CATEGORY)
                .map(RetentionPolicy::getRetentionDays)
                .filter(days -> days > 0)
                .orElse(DEFAULT_COMPLAINT_RETENTION_DAYS);

        LocalDateTime closedAt = complaint.getClosedAt();
        boolean retentionExpired = closedAt != null
                && closedAt.plusDays(retentionDays).isBefore(LocalDateTime.now());

        String reason = closedAt == null
                ? "the complaint is not closed, so its retention period has not begun"
                : "the complaint is inside its " + retentionDays + "-day retention period"
                        + " (closed " + closedAt.toLocalDate() + ")";

        auditDeletionAttempt(complaint, retentionDays, retentionExpired, reason);

        if (!retentionExpired) {
            throw new RetentionPeriodActiveException(complaint.getComplaintNumber(), reason);
        }

        complaintRepository.deleteById(id);
    }

    /**
     * Records the attempt in AUDIT_LOG, and raises a security alert when it was a retention breach.
     *
     * Written BEFORE the outcome is known, in its OWN transaction. Both details matter:
     *
     *  - BEFORE, because an audit row conditional on the delete succeeding would record only the
     *    permitted deletions and hide exactly the attempts worth investigating.
     *  - OWN TRANSACTION ({@code logInOwnTransaction}), because a refusal throws, and a row written
     *    in the caller's transaction would be rolled back by the very exception it exists to record.
     *    That is why {@code logAction} is not used here.
     *
     * Neither the audit nor the alert is allowed to fail the request. The guard's job is to stop the
     * deletion; a logging outage must not become a way to make the refusal itself fail.
     */
    private void auditDeletionAttempt(Complaint complaint, int retentionDays,
                                      boolean permitted, String reason) {
        try {
            cepcAuditService.logInOwnTransaction(
                    complaint.getComplaintNumber(),
                    permitted ? "COMPLAINT_DELETED" : "COMPLAINT_DELETE_REFUSED_RETENTION",
                    "system",
                    null,
                    reason,
                    Map.of("retentionDays", retentionDays,
                            "closedAt", String.valueOf(complaint.getClosedAt()),
                            "permitted", permitted));
        } catch (Exception e) {
            log.error("Could not audit the deletion attempt on {}: {}",
                    complaint.getComplaintNumber(), e.getMessage());
        }

        if (permitted) {
            return;
        }

        // An attempt to destroy a record inside its statutory retention period has to reach the
        // administrator who would have to investigate it. The AUDIT_LOG row above is the record;
        // nobody reads it unprompted, so it is not on its own a control.
        try {
            anomalyDetectionService.raiseDirectViolation(
                    "RETENTION_VIOLATION_ATTEMPT", "HIGH", complaint.getComplaintNumber(),
                    "Attempt to permanently delete a complaint inside its retention period: " + reason);
        } catch (Exception e) {
            log.error("Could not raise a retention-violation alert for {}: {}",
                    complaint.getComplaintNumber(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ComplaintTimeline> getTimeline(Long complaintId) {
        return timelineRepository.findByComplaintIdOrderByPerformedAtDesc(complaintId);
    }

    @Async("taskExecutor")
    @Transactional
    public void addTimelineAsync(Long complaintId, String action, String performedBy, String remarks, String fromStatus, String toStatus) {
        addTimelineAsync(complaintId, action, performedBy, null, remarks, fromStatus, toStatus);
    }

    @Async("taskExecutor")
    @Transactional
    public void addTimelineAsync(Long complaintId, String action, String performedBy, String performedByRole,
                                 String remarks, String fromStatus, String toStatus) {
        ComplaintTimeline entry = ComplaintTimeline.builder()
                .complaintId(complaintId)
                .action(action)
                .performedBy(performedBy)
                .performedByRole(performedByRole)
                .remarks(remarks)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .build();
        timelineRepository.save(entry);
    }

    public void addTimeline(Long complaintId, String action, String performedBy, String remarks, String fromStatus, String toStatus) {
        addTimeline(complaintId, action, performedBy, null, remarks, fromStatus, toStatus);
    }

    /**
     * The role-carrying form. An audit entry that names the user but not the role they acted in cannot
     * answer "who was allowed to do this", which is the question a role-wise audit is read for.
     */
    public void addTimeline(Long complaintId, String action, String performedBy, String performedByRole,
                            String remarks, String fromStatus, String toStatus) {
        ComplaintTimeline entry = ComplaintTimeline.builder()
                .complaintId(complaintId)
                .action(action)
                .performedBy(performedBy)
                .performedByRole(performedByRole == null || performedByRole.isBlank() ? null : performedByRole)
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
