package com.hrms.cms.service;

import com.hrms.cms.config.DuplicateCheckProperties;
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

import com.hrms.cms.service.mre.MreEntityCoverageService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
    private final com.hrms.cms.config.DuplicateCheckProperties duplicateCheckProperties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final OfficeAssignmentStrategyService officeAssignmentStrategyService;
    private final ComplaintOutboxPublisher complaintOutboxPublisher;
    private final OfficeRoutingService officeRoutingService;
    private final NodalOfficerRecordService nodalOfficerRecordService;
    private final RetentionPolicyRepository retentionPolicyRepository;
    private final CepcAuditService cepcAuditService;
    private final AnomalyDetectionService anomalyDetectionService;

    /**
     * Duplicate pre-check for public filing (FR-G-020 / UST87).
     *
     * <p>The user story defines a duplicate as the FULL combination:
     * (mobile OR email) + complainant name + entity name + date of disputed transaction + category.
     * All five must agree; any one differing makes the complaint unique (Scenario 3).
     *
     * <p>The narrowing fields are compared here against the columns public filing actually
     * populates — {@code entityName}, {@code categoryName} and {@code bankComplaintDate}, which
     * holds the disputed-transaction date the wizard sends as {@code transactionDate}. The earlier
     * version compared {@code bankId} and {@code categoryId}: both are null on every portal
     * complaint, so both predicates were no-ops and any returning citizen was flagged.
     *
     * <p>A narrowing value the caller did not supply is treated as NOT MATCHING rather than as
     * "ignore this field". Ignoring it is what widened the match to the whole complainant history;
     * an incomplete request cannot establish the FR-G-020 combination, so it reports no duplicate.
     */
    @Transactional(readOnly = true)
    public List<Complaint> findPotentialDuplicates(String phone, String email, String complainantName,
                                                  String entityName, String category, LocalDate disputeDate) {
        if (isBlank(complainantName) || isBlank(entityName) || isBlank(category) || disputeDate == null) {
            return List.of();
        }

        List<String> terminalStatuses = duplicateCheckProperties.getTerminalStatuses().stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

        return complaintRepository.findLiveByComplainantContact(
                        phone,
                        email,
                        terminalStatuses,
                        LocalDateTime.now().minusDays(duplicateCheckProperties.getLookbackDays()))
                .stream()
                .filter(c -> sameText(c.getComplainantName(), complainantName))
                .filter(c -> sameText(c.getEntityName(), entityName))
                .filter(c -> sameCategory(c, category))
                .filter(c -> c.getBankComplaintDate() != null
                        && c.getBankComplaintDate().toLocalDate().equals(disputeDate))
                .toList();
    }

    /**
     * The wizard sends the category code ({@code REMITTANCES}); complaints may store either that
     * code or the resolved master-data display name ({@code Remittance / Transfer}), depending on
     * whether the code resolved to a category row at filing time. Comparing both columns keeps the
     * check working across that split instead of silently never matching.
     */
    private boolean sameCategory(Complaint c, String category) {
        return sameText(c.getCategoryName(), category)
                || (c.getCategoryId() != null && categoryRepository.findById(c.getCategoryId())
                        .map(cat -> sameText(cat.getName(), category))
                        .orElse(false));
    }

    private boolean sameText(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

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

    // Non-Maintainable/portal-rejection complaints never get a complaintNumber, only a caseId, so
    // identifiers coming back from the citizen-facing letters endpoint may be either.
    @Transactional(readOnly = true)
    public Complaint getByComplaintNumberOrCaseId(String identifier) {
        return complaintRepository.findByComplaintNumber(identifier)
                .or(() -> complaintRepository.findByCaseId(identifier))
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
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
        // UST473: take the whole coverage DETERMINATION, not just the department it implies. The
        // determination is recorded on the complaint, because RBIO and CEPC are both reachable by manual
        // transfer — so the department alone cannot tell a reviewer whether a machine decided coverage or
        // a human moved the file.
        MreEntityCoverageService.Coverage coverage = routingService.resolveCoverage(entityCode);
        String department = coverage.department();

        // FR-G-013: a complaint the eligibility wizard determined Non-Maintainable gets a Case ID
        // instead of a Complaint Number, and closes immediately under the "Portal Rejection" status.
        boolean nonMaintainable = req.getNonMaintainableClauseCode() != null
                && !req.getNonMaintainableClauseCode().isBlank();

        String complaintNumber = null;
        String caseId = null;
        String officeCode = null;
        String officeName = null;
        if (nonMaintainable) {
            caseId = complaintNumberGenerator.generateCaseId(
                    department, req.getComplainantState(), req.getComplainantDistrict());
        } else {
            // generateForOffice rather than generateComplaintNumber: the latter discards the office it
            // resolved, which left rbio_office_code NULL on every portal filing. RbioComplaintListService
            // filters the officer grid on that column, so those complaints reached no office queue.
            ComplaintNumberGeneratorService.NumberedComplaint numbered =
                    complaintNumberGenerator.generateForOffice(
                            department, req.getComplainantState(), req.getComplainantDistrict());
            complaintNumber = numbered.complaintNumber();
            officeCode = numbered.officeCode();
            officeName = numbered.officeName();
        }

        Complaint complaint = Complaint.builder()
                .complaintNumber(complaintNumber)
                .caseId(caseId)
                .nonMaintainableClauseCode(req.getNonMaintainableClauseCode())
                .duplicateOfComplaintNumber(req.getDuplicateOfComplaintNumber())
                .status(nonMaintainable ? "portal_rejection" : null)
                .complaintStatusOnPortal(nonMaintainable ? "Portal Rejection" : null)
                .closureClause(nonMaintainable ? req.getNonMaintainableClauseCode() : null)
                .closedAt(nonMaintainable ? LocalDateTime.now() : null)
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

        // ENTITY_CODE is VARCHAR2(50) but REGULATED_ENTITIES.NAME is VARCHAR2(500), and entityCode here
        // holds the entity NAME. Names longer than 50 chars ("Cholamandalam Investment and Finance
        // Company Limited" is 54) made the insert fail outright, so the complaint was never filed.
        // The untruncated name is preserved on entityName, which is wide enough for it.
        complaint.setEntityCode(truncate(entityCode, 50));
        complaint.setEntityName(req.getEntityName());
        complaint.setEntityType(req.getEntityType());
        if (req.getAmountInvolved() != null) {
            complaint.setAmountInvolved(req.getAmountInvolved());
        }
        if (req.getCategoryId() != null) {
            categoryRepository.findById(req.getCategoryId())
                    .ifPresent(cat -> complaint.setCategoryName(cat.getName()));
        }
        if (complaint.getCategoryName() == null && req.getCategoryName() != null) {
            complaint.setCategoryName(req.getCategoryName());
        }
        ComplaintRoutingService.RoutingDecision routing = routingService.routeComplaint(complaint, entityCode);
        complaint.setDepartment(routing.getDepartment());
        complaint.setAssignedRole(routing.getAssignedRole());
        complaint.setAssignedOfficer(routing.getAssignedOfficer());
        complaint.setWorkflowStage(routing.getStage());

        // UST473: the determination itself, so a reviewer can see WHY this forum was chosen and which
        // Scheme's rules were applied. schemeVersion had no writer at all before this.
        complaint.setSchemeCoverageStatus(coverage.status() != null ? coverage.status().name() : null);
        complaint.setSchemeCoverageReason(coverage.reason());
        complaint.setSchemeVersion(coverage.schemeVersion());

        // Office capacity routing: the office that ACCEPTS the complaint may not be the one it was
        // numbered against, because a full office overflows to its configured next hop. The accepting
        // office is what must land on the row — the complaint number keeps the original office code
        // forever, so reading the office back out of the number would name the wrong one.
        String acceptingOffice = officeCode;
        if (officeCode != null) {
            acceptingOffice = resolveAcceptingOffice(officeCode, complaint.getComplaintNumber());
        }
        complaint.setRbioOfficeCode(acceptingOffice);

        // routeFromPublicPortal deliberately leaves an RBIO complaint's officer null because the officer
        // is the OFFICE's choice, not routing's (UST468-472) — but the step meant to make that choice
        // during filing was never wired up, so every portal-filed RBIO complaint sat with no officer and
        // no office, invisible to the RBIO grid. The office's strategy may also override the router's
        // pick; an unassignable strategy must NOT blank an officer the router already found, or the
        // complaint would be taken away from someone able to act on it.
        if (acceptingOffice != null && !"CEPC".equals(routing.getDepartment())) {
            assignRbioOfficerForOffice(complaint, acceptingOffice, req.getEntityName());
        }

        Complaint saved = complaintRepository.save(complaint);

        // UST473: an entity nobody could identify routes to CEPC, but that must be VISIBLE rather than
        // silently accepted — the citizen's forum was decided without the entity actually being known.
        if (coverage.needsReview()) {
            addTimeline(saved.getId(), "scheme_coverage_check", "System",
                    "Entity coverage is " + coverage.status() + " — routed to " + routing.getDepartment()
                            + " pending confirmation: " + coverage.reason(),
                    null, null);
        }

        // UST569. Hooked here because the defect being guarded is the HOOK going missing: the service can
        // be perfectly correct and still never be called, leaving the complaint invisible to the
        // staleness escalations that chase the entity for a reply.
        if (!nonMaintainable) {
            // entityCode, not req.getEntityName(): a complaint filed by bankId alone carries no entity
            // name on the request, and the NO/PNO lookup keys on the entity — passing null would make
            // the record unattributable to the entity it is meant to chase.
            ensureNodalOfficerRecord(saved, entityCode, officeName);
        }

        if (nonMaintainable) {
            addTimeline(saved.getId(), "portal_rejection", "System",
                    "Complaint determined Non-Maintainable under clause " + req.getNonMaintainableClauseCode()
                            + "; Case ID issued instead of a complaint number",
                    null, "portal_rejection");
        } else {
            addTimeline(saved.getId(), "filed", "System",
                    "Complaint filed and routed to " + routing.getDepartment() + " (" + routing.getReason() + ")",
                    null, "pending");
        }

        // A non-maintainable complaint is closed on arrival and has no complaint number, so there is no
        // downstream processing to hand off — emitting an ingestion event for one would put a closed case
        // into the assignment service's queue.
        if (!nonMaintainable) {
            // Written in THIS transaction so the event and the complaint commit together. The @Async
            // kafkaTemplate.send below cannot give that guarantee: it runs on another thread, so it can
            // fire before the commit (or after a rollback), and it swallows a broker outage — which left
            // the complaint row committed and no officer ever told about it. cms-outbox-publisher drains
            // OUTBOX_EVENT with retries, so a broker outage now delays the handoff instead of losing it.
            complaintOutboxPublisher.publishIngested(saved);
        }

        // Retained alongside the outbox so existing live Kafka consumers keep receiving events during the
        // cutover. The outbox row is the durable record; this is best-effort and may be dropped once all
        // consumers read from the outbox-published topic.
        eventPublisher.publishComplaintIngested(saved);

        return saved;
    }

    /**
     * Statuses that settle a complaint. Once a record reaches one of these it is a closed matter, and
     * amending it in place would rewrite history rather than append to it: a reopen is a workflow
     * action with its own audit entry, not a field edit.
     */
    private static final Set<String> TERMINAL_STATUSES = Set.of("closed", "withdrawn", "rejected");

    /**
     * Picks the officer for an RBIO complaint using its office's configured strategy.
     *
     * <p>Assignment failure must not fail the filing. A complaint saved with no officer is recoverable —
     * it shows in the office's unassigned queue and a supervisor can allocate it — whereas rejecting the
     * submission loses the citizen's entire form and tells them nothing useful.
     */
    private void assignRbioOfficerForOffice(Complaint complaint, String officeCode, String entityName) {
        try {
            OfficeAssignmentStrategyService.Resolution resolution =
                    officeAssignmentStrategyService.resolveOfficer(
                            officeCode, entityName, complaint.getCategoryId());
            // Only overwrite when the strategy actually produced someone. isAssigned() false means the
            // office could not place it, and the router's earlier pick is better than nobody.
            if (resolution != null && resolution.isAssigned()) {
                complaint.setAssignedOfficer(resolution.officerId());
            }
        } catch (Exception e) {
            // Keeps whatever the router assigned; the office queue is the recovery path.
        }
    }

    /**
     * The office that will actually hold the complaint, which is not always the one it was numbered
     * against: a primary office at capacity overflows to its configured next hop.
     *
     * <p>Falls back to the numbered office when capacity is unknown (no config row, or the call fails).
     * That is the fail-closed direction — an unconfigured office must not silently place the complaint
     * somewhere else.
     */
    private String resolveAcceptingOffice(String numberedOfficeCode, String complaintNumber) {
        try {
            Map<String, Object> placement = officeRoutingService.routeToOffice(numberedOfficeCode, false);
            if (placement == null) return numberedOfficeCode;

            Object status = placement.get("status");
            if (!OfficeRoutingService.STATUS_NOT_FOUND.equals(status)
                    && !OfficeRoutingService.STATUS_AT_CAPACITY.equals(status)) {
                Object officeId = placement.get("officeId");
                if (officeId != null && !officeId.toString().isBlank()) {
                    return officeId.toString();
                }
            }
        } catch (Exception e) {
            // Fall through to the numbered office.
        }
        return numberedOfficeCode;
    }

    /** Creates the NO/PNO tracking record for a filed complaint, scoped to its processing office. */
    private void ensureNodalOfficerRecord(Complaint saved, String entityName, String officeName) {
        try {
            // Never null: the resolver takes a blank name (it falls back to the office), but a null would
            // make the record's own entityName column null and unattributable.
            String resolvedEntity = entityName != null && !entityName.isBlank()
                    ? entityName
                    : (saved.getEntityName() != null ? saved.getEntityName() : "");
            nodalOfficerRecordService.ensureRecordExists(
                    saved.getId(), saved.getComplaintNumber(), resolvedEntity, officeName);
        } catch (Exception e) {
            // A missing NO record must not fail a complaint the citizen correctly filed.
        }
    }

    /** Trims {@code value} to {@code max} characters so a long value cannot fail the insert. */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

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
    public Complaint updateComplaintDirectly(Complaint complaint) {
        return complaintRepository.save(complaint);
    }

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
    public Complaint withdrawComplaint(String complaintNumber, String reason, String remarks) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        // UST105/UST107: withdrawal is barred for every status that has already disposed of the
        // complaint. PORTAL_REJECTION, ADJUDICATED and CONCILIATED were missing, so a complaint settled
        // by award or conciliation could still be withdrawn — overwriting a concluded outcome and the
        // closure clause that recorded it. SENT_TO_OTHER/FORWARDED_EXTERNAL are the user story's
        // "Sent to other Department"/"Sent to other Regulatory Bodies", which it also excludes.
        String status = complaint.getStatus() != null ? complaint.getStatus().toUpperCase() : "";
        if (List.of("CLOSED", "RESOLVED", "REJECTED", "WITHDRAWN", "PORTAL_REJECTION",
                    "ADJUDICATED", "CONCILIATED", "SENT_TO_OTHER", "FORWARDED_EXTERNAL").contains(status)) {
            throw new IllegalStateException("Complaints cannot be withdrawn.");
        }

        String withdrawRemark = "Withdrawn by complainant. Reason: " + reason;
        if (remarks != null && !remarks.isEmpty() && !remarks.equals(reason)) {
            withdrawRemark += " — " + remarks;
        }

        String oldStatus = complaint.getStatus();
        complaint.setStatus("withdrawn");
        complaint.setClosedAt(LocalDateTime.now());
        complaint.setClosureClause("16(6)");
        complaint.setClosureClauseDescription("Closed - complaint withdrawn by complainant");

        Complaint saved = complaintRepository.save(complaint);

        ComplaintTimeline entry = ComplaintTimeline.builder()
                .complaintId(saved.getId())
                .action("WITHDRAWN")
                .performedBy("complainant")
                .remarks(withdrawRemark)
                .fromStatus(oldStatus)
                .toStatus("withdrawn")
                .build();
        timelineRepository.save(entry);

        return saved;
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
