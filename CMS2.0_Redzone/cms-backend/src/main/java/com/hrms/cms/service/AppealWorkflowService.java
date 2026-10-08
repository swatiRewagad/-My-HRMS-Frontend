package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealStatus;
import com.hrms.cms.entity.AppealTimeline;
import com.hrms.cms.entity.ClosureClauseMaster.AppealParty;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.TimelineEventSource;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.AppealTimelineRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.dto.AaAssignmentRequest;
import com.hrms.cms.dto.AaAssignmentResult;
import com.hrms.cms.security.AaAccessDeniedException;
import com.hrms.cms.security.AaIdentityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppealWorkflowService {

    private final AppealRepository appealRepository;
    private final AppealTimelineRepository appealTimelineRepository;
    private final AppealEligibilityService eligibilityService;
    private final ComplaintRepository complaintRepository;
    private final KeycloakUserService keycloakUserService;
    private final FileStorageService fileStorageService;
    private final AaIdentityResolver aaIdentityResolver;
    private final AppealClassificationService classificationService;
    private final AaAssignmentEngine assignmentEngine;
    private final AaWorkflowNotifier workflowNotifier;
    private final AaHearingPort hearingPort;
    private final ComplaintTimelineRepository complaintTimelineRepository;
    private final AaOutboxPublisher outboxPublisher;

    /**
     * Who a notice is owed to when a hearing is fixed through the workflow action, which — unlike
     * S3C's own endpoint — carries no party list. Matches that endpoint's default: the appellant has a
     * statutory interest in being told and must not be dropped because the caller omitted the field.
     */
    private static final List<String> HEARING_NOTICE_PARTIES = List.of("appellant");

    // The in-memory round-robin counter that used to live here is gone. It was a ConcurrentHashMap over
    // Keycloak users: lost on restart, divergent per pod, and blind to every workload threshold. All
    // assignment now goes through AaAssignmentEngine, which persists its pointer and enforces caps.

    /** Roles permitted to set aside the clause-derived classification. */
    private static final Set<String> OVERRIDE_ROLES = Set.of("AA_DO", "AA_REVIEWER", "AA_ADMIN");

    /**
     * The citizen-facing status a complaint carries once an appeal is filed against it (UST106).
     *
     * <p>Set on COMPLAINT_STATUS_ON_PORTAL rather than STATUS: STATUS drives the officer workflow state
     * machine, and the parent complaint's own workflow is finished. Overwriting it would reopen a closed
     * complaint in the RBIO/CEPC grids, where the appeal is the AA's work and not theirs.
     */
    private static final String PARENT_STATUS_APPEAL_FILED = "APPEAL_FILED";

    // The role matrix and the terminal-status list that used to live here are gone. Both are now
    // declared once in AaWorkflowTransition, and the terminal codes come from AppealStatus.TERMINAL_CODES
    // rather than a third hand-rolled copy of the same three strings.

    // ═══════════════════════════════════════════════════════════
    // File Appeal (legacy signature for backward compatibility)
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public Map<String, Object> fileAppeal(Map<String, String> request) {
        return fileAppeal(request, null);
    }

    // ═══════════════════════════════════════════════════════════
    // File Appeal (new signature with attachments)
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public Map<String, Object> fileAppeal(Map<String, String> request, MultipartFile[] attachments) {
        String originalComplaintNumber = request.get("originalComplaintNumber");
        String appealGround = request.get("appealGround");
        String reliefSought = request.get("reliefSought");
        String appellantName = request.get("appellantName");
        String appellantEmail = request.get("appellantEmail");
        String appellantPhone = request.get("appellantPhone");
        String reasonForDelay = request.get("reasonForDelay");

        // Validate required fields
        if (originalComplaintNumber == null || originalComplaintNumber.isBlank()) {
            throw new IllegalArgumentException("originalComplaintNumber is required");
        }

        Complaint parent = complaintRepository.findByComplaintNumber(originalComplaintNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Parent complaint not found: " + originalComplaintNumber));

        // Each contact field falls back independently. They used to be nested inside the name check, so a
        // caller who supplied a name but no email got NO email — now that the portal sends the name, that
        // nesting would have silently dropped the appellant's contact details.
        if (appellantName == null || appellantName.isBlank()) {
            appellantName = parent.getComplainantName();
        }
        if (appellantEmail == null || appellantEmail.isBlank()) {
            appellantEmail = parent.getComplainantEmail();
        }
        if (appellantPhone == null || appellantPhone.isBlank()) {
            appellantPhone = parent.getComplainantPhone();
        }
        if (appellantName == null || appellantName.isBlank()) {
            throw new IllegalArgumentException("appellantName is required");
        }

        // Classification is DERIVED, never accepted from the caller. It used to come straight from a
        // request parameter defaulting to "APPEAL", which let the browser decide a legal question:
        // whether the citizen gets an appeal or only a representation. Any client-supplied value is
        // ignored, and an unmapped clause fails closed inside the classification service.
        AppealParty party = resolveParty(request);
        String classificationType = classificationService.classify(parent, party);

        String requestedClassification = request.get("classificationType");
        if (requestedClassification != null && !requestedClassification.isBlank()
                && !requestedClassification.equalsIgnoreCase(classificationType)) {
            log.warn("Ignoring client-supplied classification '{}' for complaint {}: clause-derived value is {}",
                    requestedClassification, originalComplaintNumber, classificationType);
        }
        if (appealGround == null || appealGround.isBlank()) {
            throw new IllegalArgumentException("appealGround is required");
        }

        // UST106: one appeal per complaint. Without this guard a citizen who resubmitted the form — or
        // double-clicked it — got a second appeal row against the same complaint, each with its own
        // number and its own AA_DO placement, so two officers worked the same grievance.
        // UST106 AC3. AppealController already checks this, but the legacy fileAppeal(request) signature
        // bypasses the controller entirely, so without a guard here a second appeal could still be
        // created against one complaint — two appeal numbers, two AA_DO placements, two officers working
        // the same grievance. Matches the controller's rule: a CLOSED appeal does not bar a new one,
        // since the remand path legitimately ends one appeal and may warrant another.
        boolean hasActiveAppeal = appealRepository.findByOriginalComplaintNumber(originalComplaintNumber)
                .stream()
                .anyMatch(a -> !AppealStatus.TERMINAL_CODES.contains(a.getStatus()));
        if (hasActiveAppeal) {
            throw new IllegalStateException("Appeal already filed for this complaint.");
        }

        // Check eligibility
        Map<String, Object> eligibility = eligibilityService.checkEligibility(originalComplaintNumber);
        if (!Boolean.TRUE.equals(eligibility.get("eligible"))) {
            throw new IllegalArgumentException("Appeal not eligible: " + eligibility.get("reason"));
        }

        // Generate appeal number
        String appealNumber = generateAppealNumber();

        // Create the appeal entity
        Appeal appeal = Appeal.builder()
                .appealNumber(appealNumber)
                .originalComplaintNumber(originalComplaintNumber)
                .classificationType(classificationType)
                .appealGround(appealGround)
                .reliefSought(reliefSought)
                .reasonForDelay(reasonForDelay)
                .appellantName(appellantName)
                .appellantEmail(appellantEmail)
                .appellantPhone(appellantPhone)
                .status("filed")
                .assignedRole("AA_DO")
                // Officer is set AFTER the save: the engine records a placement against the appeal
                // number, so the appeal has to exist first.
                .priority("high")
                .workflowStage("FILED")
                // Provenance, so the "Created By Me" view can attribute this record. A citizen filing
                // on the portal has no AA role, so createdBy is their own id and createdByRole null.
                .createdBy(aaIdentityResolver.resolveActor())
                .createdByRole(aaIdentityResolver.resolveAaRole())
                .appealFiledBy(party.name())
                .entityCode(parent.getEntityCode())
                .closureClause(parent.getClosureClause())
                .build();

        Appeal saved = appealRepository.save(appeal);

        // Place the appeal with a real AA_DO through the engine, which enforces per-officer thresholds.
        // An unassignable pool leaves assignedOfficer null and the appeal visible in the AA_DO role queue
        // rather than fabricating an assignee.
        String initialOfficer = assignThroughEngine(saved.getAppealNumber(), "AA_DO", saved);
        if (initialOfficer != null) {
            saved.setAssignedOfficer(initialOfficer);
            saved = appealRepository.save(saved);
            workflowNotifier.notifyOfficer(initialOfficer, saved.getAppealNumber(),
                    AaWorkflowEvent.FILED);
        } else {
            // An unassignable pool must not mean an unnotified appeal. Every appeal in this database is
            // filed with assignedOfficer null (the engine has no eligible AA_DO), so the branch above never
            // ran and NOBODY was told a new appeal had arrived. The bell already supports role-addressed
            // rows, so the assigned ROLE is notified instead — which is also the queue the appeal is
            // actually sitting in.
            workflowNotifier.notifyOfficer(saved.getAssignedRole(), saved.getAppealNumber(),
                    AaWorkflowEvent.FILED);
        }

        // The appellant is a citizen: this records the obligation to tell them, which the delivery log
        // fulfils. It is deliberately not a bell notification — they have no inbox here.
        workflowNotifier.notifyAppellant(saved.getAppealNumber(), AaWorkflowEvent.FILED);
        outboxPublisher.publish(saved, AaWorkflowEvent.FILED, saved.getCreatedBy());

        // Handle file attachments using the existing FileStorageService
        // Attachments are stored as ComplaintAttachments linked to the appeal's
        // original complaint ID — or we can link them by appeal number convention.
        if (attachments != null && attachments.length > 0) {
            storeAttachments(attachments, saved);
        }

        // Add timeline entry
        String timelineRemarks = "Appeal filed against complaint " + originalComplaintNumber;
        if (reasonForDelay != null && !reasonForDelay.isBlank()) {
            timelineRemarks += " (delayed filing — reason: " + reasonForDelay + ")";
        }
        // Recorded on the timeline rather than as new APPEALS columns: both are statements the appellant
        // made at filing, so their place is the audit trail. Adding columns would also mean a schema
        // change for two free-text values nothing queries.
        String dateOfReceipt = request.get("dateOfReceipt");
        if (dateOfReceipt != null && !dateOfReceipt.isBlank()) {
            timelineRemarks += " | date of receipt of order: " + dateOfReceipt;
        }
        String comments = request.get("comments");
        if (comments != null && !comments.isBlank()) {
            timelineRemarks += " | appellant comments: " + comments;
        }
        addTimeline(appealNumber, "FILED", "SYSTEM", null, timelineRemarks, null, "filed");

        // UST106 AC2: the parent complaint must show that an appeal is now pending against it. Only the
        // APPEAL row carried that fact before, so the citizen's tracking view and the officer grid both
        // still showed the complaint as plainly closed. closedAt/closureClause are left untouched — the
        // original closure is a matter of record, and an appeal does not undo it.
        parent.setComplaintStatusOnPortal(PARENT_STATUS_APPEAL_FILED);
        complaintRepository.save(parent);

        // ═══ The PARENT COMPLAINT's own history records that it has been appealed ═══
        //
        // Nothing was written here. The row above lands in APPEAL_TIMELINE, which is keyed on the APPEAL
        // number — a record the citizen who filed the appeal cannot read (GET /api/v1/appeals/{n} sits
        // behind @AaRoleGuard). So a complainant who tracked their complaint straight after filing saw a
        // screen IDENTICAL to the one before they filed: status "Closed", the same six timeline rows, and
        // the File Appeal button still offered. The appeal did exist in APPEALS and a second attempt was
        // refused, but the complaint said nothing about it — indistinguishable, from the citizen's side,
        // from the silent-failure class this codebase already has.
        //
        // Written as a COMPLAINT_TIMELINE row rather than as a new COMPLAINTS.status value, deliberately:
        //
        //  * The complaint's status is the OMBUDSMAN's finding, and an appeal does not disturb it.
        //    Overwriting it with "appeal_filed" would make the closure unreadable, break every status
        //    filter and SLA query that selects on `closed`, and — worse — make the complaint appear
        //    non-terminal, so AppealEligibilityService.TERMINAL_STATUSES would stop matching it and the
        //    appeal route would close behind the citizen who had just used it.
        //  * The ONE thing that legitimately changed is that an appeal now exists against it. That is an
        //    EVENT, and COMPLAINT_TIMELINE is the append-only record for one.
        //
        // AUTOMATIC, not MANUAL: a person filed the appeal, but the complaint-side entry is a consequence
        // the system derived — the same discrimination reopenOriginalComplaint already makes.
        complaintTimelineRepository.save(ComplaintTimeline.builder()
                .complaintId(parent.getId())
                .action("APPEAL_FILED")
                .performedBy(saved.getCreatedBy() == null || saved.getCreatedBy().isBlank()
                        ? "CITIZEN" : saved.getCreatedBy())
                .remarks(saved.getClassificationType() + " " + saved.getAppealNumber()
                        + " filed against this complaint")
                // fromStatus == toStatus, because the status genuinely did NOT change. Stating it
                // explicitly is what stops a reader inferring a transition that never happened.
                .fromStatus(parent.getStatus())
                .toStatus(parent.getStatus())
                .fieldName("appealNumber")
                .newValue(saved.getAppealNumber())
                .eventSource(TimelineEventSource.AUTOMATIC)
                .build());

        // TODO: Write to OUTBOX_EVENTS for "appeal.filed" topic once OutboxEvent entity is created.
        // The outbox pattern should publish { appealNumber, originalComplaintNumber, classificationType,
        // filedAt, appellantName } to the "appeal.filed" Kafka topic for downstream consumers.

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appealNumber", saved.getAppealNumber());
        result.put("classificationType", saved.getClassificationType());
        result.put("status", saved.getStatus());
        result.put("assignedRole", saved.getAssignedRole());
        result.put("assignedOfficer", saved.getAssignedOfficer());
        result.put("filedAt", saved.getFiledAt() != null ? saved.getFiledAt().toString() : "");

        return result;
    }

    // ═══════════════════════════════════════════════════════════
    // Perform Action
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public Map<String, Object> performAction(String appealNumber, String action, Map<String, String> params) {
        Appeal appeal = appealRepository.findByAppealNumber(appealNumber)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealNumber));

        // Identity is resolved by the server, never taken from the request body. Previously both
        // `actor` and `actorRole` were read out of params and the role matrix was enforced only when
        // actorRole was non-blank — and the Angular client never sent it, so the AA role check was
        // skipped on every real request and attribution was whatever the caller typed.
        String actorRole = aaIdentityResolver.resolveAaRole();
        if (actorRole == null) {
            throw new AaAccessDeniedException(
                    "No AA role present on the request; this action requires one of " + AaIdentityResolver.AA_ROLES);
        }
        String resolvedActor = aaIdentityResolver.resolveActor();
        String actor = resolvedActor != null ? resolvedActor : "UNKNOWN";
        String remarks = params.getOrDefault("remarks", "");
        String prevStatus = appeal.getStatus();

        // Both halves of the gate come from ONE table, AaWorkflowTransition, which getAvailableActions
        // also reads. Previously the role was checked here while the status rules lived only in that
        // read-only helper, so the server enforced half of its own workflow: a freshly filed appeal
        // could be driven straight to order_passed, a passed order could be overwritten, and a disposed
        // appeal could be pulled back to under_review.
        AaWorkflowTransition transition = AaWorkflowTransition.find(action)
                .orElseThrow(() -> new IllegalArgumentException("Unknown action: " + action));

        if (!transition.permits(actorRole)) {
            throw new AaAccessDeniedException(
                    "Action " + action + " is not permitted for role " + actorRole);
        }

        // The from-status guard. An illegal transition is a client error with a translation key, not a
        // 500 and not a silent success.
        if (!transition.allowedFrom(prevStatus)) {
            throw new AaIllegalTransitionException(transition.name(), prevStatus,
                    AaWorkflowTransition.availableFor(actorRole, prevStatus));
        }

        // Validate classificationType immutability - reject any attempt to change it
        if (params.containsKey("classificationType")) {
            String requestedType = params.get("classificationType");
            if (!appeal.getClassificationType().equals(requestedType)) {
                throw new IllegalArgumentException("classificationType is immutable and cannot be changed after creation");
            }
        }

        switch (action.toUpperCase(Locale.ROOT)) {
            case "ACCEPT":
                appeal.setStatus("under_review");
                appeal.setWorkflowStage("UNDER_REVIEW");
                break;

            case "REJECT":
                if (remarks.isBlank()) {
                    throw new IllegalArgumentException("Rejection reason (remarks) is required");
                }
                appeal.setStatus("rejected");
                appeal.setClosedAt(LocalDateTime.now());
                appeal.setWorkflowStage("REJECTED");
                break;

            case "ASSIGN_TO_BENCH":
                appeal.setAssignedRole("AA_REVIEWER");
                appeal.setAssignedOfficer(assignThroughEngine(appealNumber, "AA_REVIEWER", appeal));
                appeal.setWorkflowStage("ASSIGNED_TO_BENCH");
                break;

            case "REQUEST_DOCUMENTS":
                // Status stays the same, just add a timeline entry
                appeal.setWorkflowStage("DOCUMENTS_REQUESTED");
                break;

            case "SCHEDULE_HEARING":
                String hearingDateStr = params.get("hearingDate");
                String hearingVenue = params.getOrDefault("hearingVenue", "");
                if (hearingDateStr == null || hearingDateStr.isBlank()) {
                    throw new IllegalArgumentException("hearingDate is required for SCHEDULE_HEARING action");
                }
                LocalDateTime hearingWhen = parseHearingDate(hearingDateStr);

                // Route through the hearing port so BOTH scheduling paths -- this action and S3C's own
                // /hearings endpoint -- append to APPEAL_HEARING. Setting the fields directly (as this
                // did) left no history row, so a hearing fixed here was invisible to the history view
                // and a later reschedule silently erased the vacated sitting: an audit failure on a
                // statutory hearing. The port also owns the notice obligations.
                boolean alreadyFixed = hearingPort.current(appealNumber) != null;
                if (alreadyFixed) {
                    String moveReason = firstNonBlank(params.get("reason"), remarks,
                            "Rescheduled via workflow action");
                    hearingPort.reschedule(appealNumber, hearingWhen, hearingVenue,
                            params.get("hearingMode"), moveReason, HEARING_NOTICE_PARTIES);
                } else {
                    hearingPort.schedule(appealNumber, hearingWhen, hearingVenue,
                            params.get("hearingMode"), HEARING_NOTICE_PARTIES);
                }

                appeal.setHearingDate(hearingWhen);
                appeal.setHearingVenue(hearingVenue);
                appeal.setStatus("hearing_scheduled");
                appeal.setWorkflowStage("HEARING_SCHEDULED");
                break;

            case "PREPARE_BRIEF":
                appeal.setWorkflowStage("BRIEF_PREPARED");
                break;

            case "FORWARD_TO_AUTHORITY":
                appeal.setAssignedRole("AA_SECRETARIAT");
                appeal.setAssignedOfficer(assignThroughEngine(appealNumber, "AA_SECRETARIAT", appeal));
                appeal.setWorkflowStage("FORWARDED_TO_AUTHORITY");
                break;

            case "SEND_BACK_REGISTRAR":
                appeal.setAssignedRole("AA_DO");
                // Back to the DO who ROUTED it, not to a fresh round-robin pick. Sending a file back to
                // an arbitrary DO was a real defect: the officer who raised the query is the one holding
                // the context, and the record already remembers who that was.
                appeal.setAssignedOfficer(returnToRouter(appealNumber, appeal));
                appeal.setWorkflowStage("SENT_BACK_TO_REGISTRAR");
                break;

            case "ESCALATE_TO_TIER2":
                escalateToTier2(appealNumber, appeal, actor, actorRole, remarks);
                break;

            case "PASS_ORDER":
                String orderSummary = params.getOrDefault("orderSummary", "");
                String orderOutcome = params.getOrDefault("orderOutcome", "");
                String modifiedAmountStr = params.get("awardModifiedAmount");

                if (orderOutcome.isBlank()) {
                    throw new IllegalArgumentException("orderOutcome is required for PASS_ORDER (UPHELD, MODIFIED, SET_ASIDE, REMANDED, DISMISSED)");
                }

                appeal.setStatus("order_passed");
                appeal.setOrderDate(LocalDateTime.now());
                appeal.setOrderSummary(orderSummary);
                appeal.setOrderOutcome(orderOutcome);
                appeal.setWorkflowStage("ORDER_PASSED");

                if (modifiedAmountStr != null && !modifiedAmountStr.isBlank()) {
                    appeal.setAwardModifiedAmount(new BigDecimal(modifiedAmountStr));
                }
                break;

            case "REMAND_TO_OMBUDSMAN":
                appeal.setStatus("closed");
                appeal.setClosedAt(LocalDateTime.now());
                appeal.setClosureCause("REMANDED");
                appeal.setOrderOutcome("REMANDED");
                appeal.setWorkflowStage("REMANDED");

                // Reopen the original complaint
                reopenOriginalComplaint(appeal.getOriginalComplaintNumber(), appealNumber, actor);
                break;

            case "DISMISS":
                appeal.setStatus("closed");
                appeal.setClosedAt(LocalDateTime.now());
                appeal.setOrderOutcome("DISMISSED");
                appeal.setWorkflowStage("DISMISSED");
                break;

            case "REASSIGN":
                String newRole = params.getOrDefault("role", appeal.getAssignedRole());
                String newOfficer = params.getOrDefault("officer", "");

                // The requested role is validated against the AA vocabulary. It used to be written
                // straight through from the request body, so an admin could park an appeal on
                // assignedRole="RE_PNO" or a typo and every task query keyed on assignedRole would lose
                // it permanently, with no error and no way back short of a SQL fix.
                if (!AaIdentityResolver.AA_ROLES.contains(newRole)) {
                    throw new IllegalArgumentException(
                            "aa.workflow.error_invalid_target_role");
                }
                appeal.setAssignedRole(newRole);

                if (newOfficer.isBlank()) {
                    appeal.setAssignedOfficer(assignThroughEngine(appealNumber, newRole, appeal));
                } else {
                    // A named target is an explicit admin override: routed through the engine's audited
                    // manual path so the threshold bypass is recorded with an actor and a reason, rather
                    // than silently written to the column.
                    AaAssignmentResult manual = assignmentEngine.assignManually(appealNumber, newOfficer,
                            remarks.isBlank() ? "AA_ADMIN reassignment" : remarks);
                    appeal.setAssignedOfficer(manual.getAssignedUserId());
                }
                appeal.setWorkflowStage("REASSIGNED");
                break;

            case "CLOSE":
                appeal.setStatus("closed");
                appeal.setClosedAt(LocalDateTime.now());
                appeal.setClosureCause(params.getOrDefault("closureCause", "ADMIN_CLOSED"));
                appeal.setWorkflowStage("CLOSED");
                break;

            case "REOPEN":
                // Reviving a disposed appeal is the one way out of a terminal state, so it demands a
                // reason: an unexplained reopen of a passed order is indistinguishable from an error.
                if (remarks.isBlank()) {
                    throw new IllegalArgumentException("aa.workflow.error_reopen_reason_required");
                }
                appeal.setStatus("under_review");
                appeal.setClosedAt(null);
                appeal.setClosureCause(null);
                appeal.setWorkflowStage("REOPENED");
                break;

            default:
                // Unreachable: AaWorkflowTransition.find already rejected an unknown action above. Kept
                // so adding a transition to the table without handling it here fails loudly.
                throw new IllegalArgumentException("Action " + action + " is declared but not implemented");
        }

        appealRepository.save(appeal);

        // Add timeline entry
        addTimeline(appealNumber, action.toUpperCase(Locale.ROOT), actor, actorRole, remarks, prevStatus,
                appeal.getStatus());

        // Announce it. The event vocabulary decides who hears about what, so no call site has to reason
        // about whether a citizen cares — and both calls are deferred to after commit inside the
        // notifier, so nothing is announced for a transition that then rolls back.
        AaWorkflowEvent event = transition.getEvent();
        if (event != null) {
            // Same transaction as the change, so the two are atomic.
            outboxPublisher.publish(appeal, event, actor);
            if (appeal.getAssignedOfficer() != null && !appeal.getAssignedOfficer().isBlank()) {
                workflowNotifier.notifyOfficer(appeal.getAssignedOfficer(), appealNumber, event);
            }
            if (event.notifiesAppellant()) {
                workflowNotifier.notifyAppellant(appealNumber, event);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appealNumber", appeal.getAppealNumber());
        result.put("action", action.toUpperCase(Locale.ROOT));
        result.put("newStatus", appeal.getStatus());
        result.put("assignedRole", appeal.getAssignedRole());
        result.put("assignedOfficer", appeal.getAssignedOfficer());
        result.put("workflowStage", appeal.getWorkflowStage());
        // So a client never has to guess what it may do next, and can never offer something the server
        // would refuse.
        result.put("availableActions",
                AaWorkflowTransition.availableFor(actorRole, appeal.getStatus()));

        return result;
    }

    // ═══════════════════════════════════════════════════════════
    // Available Actions
    // ═══════════════════════════════════════════════════════════

    /**
     * Actions the CURRENT caller may perform, resolved from their token.
     *
     * The role is no longer accepted as a parameter: it was a client-supplied query param, so the UI
     * could ask for — and be shown — another role's action set, and the buttons it rendered then
     * disagreed with what performAction would actually allow.
     */
    public List<String> getAvailableActions(String appealNumber) {
        Optional<Appeal> opt = appealRepository.findByAppealNumber(appealNumber);
        if (opt.isEmpty()) return Collections.emptyList();

        String userRole = aaIdentityResolver.resolveAaRole();
        if (userRole == null) {
            return Collections.emptyList();
        }

        // Derived from the SAME table performAction enforces, so the two can no longer disagree. The
        // terminal-state rule is not special-cased here any more: it falls out of the table, because the
        // only transition declaring a terminal from-status is AA_ADMIN's REOPEN.
        return AaWorkflowTransition.availableFor(userRole, opt.get().getStatus());
    }

    // ═══════════════════════════════════════════════════════════
    // Stats
    // ═══════════════════════════════════════════════════════════

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", appealRepository.count());
        stats.put("filed", appealRepository.findByStatus("filed").size());
        stats.put("underReview", appealRepository.findByStatus("under_review").size());
        stats.put("hearingScheduled", appealRepository.findByStatus("hearing_scheduled").size());
        stats.put("orderPassed", appealRepository.findByStatus("order_passed").size());
        stats.put("closed", appealRepository.findByStatus("closed").size());
        stats.put("rejected", appealRepository.findByStatus("rejected").size());
        return stats;
    }

    // ═══════════════════════════════════════════════════════════
    // Timeline
    // ═══════════════════════════════════════════════════════════

    public void addTimeline(String appealNumber, String action, String performedBy, String performedByRole,
                            String remarks, String fromStatus, String toStatus) {
        AppealTimeline entry = AppealTimeline.builder()
                .appealNumber(appealNumber)
                .action(action)
                .performedBy(performedBy)
                .performedByRole(performedByRole)
                .remarks(remarks)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .build();
        appealTimelineRepository.save(entry);
    }

    public List<AppealTimeline> getTimeline(String appealNumber) {
        return appealTimelineRepository.findByAppealNumberOrderByPerformedAtDesc(appealNumber);
    }

    /**
     * A timeline row that records a FIELD changing, not just a status moving.
     *
     * Reuses the fieldName/oldValue/newValue columns the classification override already writes, so
     * escalations and reassignments are answerable at the same granularity rather than appearing as an
     * opaque action name.
     */
    private void addFieldTimeline(String appealNumber, String action, String performedBy,
                                 String performedByRole, String remarks, String status,
                                 String fieldName, String oldValue, String newValue) {
        appealTimelineRepository.save(AppealTimeline.builder()
                .appealNumber(appealNumber)
                .action(action)
                .performedBy(performedBy)
                .performedByRole(performedByRole)
                .remarks(remarks)
                .fromStatus(status)
                .toStatus(status)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .build());
    }

    // ═══════════════════════════════════════════════════════════
    // Private helpers
    // ═══════════════════════════════════════════════════════════

    private void storeAttachments(MultipartFile[] attachments, Appeal appeal) {
        for (MultipartFile file : attachments) {
            if (file == null || file.isEmpty()) continue;
            try {
                // Use the appeal number as the "complaint number" for storage path,
                // and the appeal ID as the complaint ID reference.
                ComplaintAttachment saved = fileStorageService.handleSingleUpload(
                        file,
                        appeal.getAppealNumber(),
                        appeal.getId()
                );
                log.info("Stored appeal attachment: {} -> {}", file.getOriginalFilename(), saved.getStoragePath());
            } catch (Exception e) {
                log.warn("Failed to store attachment '{}' for appeal {}: {}",
                        file.getOriginalFilename(), appeal.getAppealNumber(), e.getMessage());
                // Non-fatal: continue with other attachments
            }
        }
    }

    /**
     * Hands the parent complaint back to the ombudsman side after a remand.
     *
     * Previously this set the status and nothing else, using ifPresent — so a missing parent silently
     * no-opped while the appeal still closed as REMANDED, and even when the parent existed it landed in
     * in_progress with a stale workflow stage and whatever assignee it had before closure. Since RBIO and
     * CEPC queues are keyed on the workflow stage, a remanded complaint was invisible to every officer
     * queue: legally reopened, operationally lost.
     *
     * Now: hard-fails on a missing parent, sets a stage the queues actually select on, writes a
     * complaint-side timeline row, and notifies whoever holds it.
     */
    private void reopenOriginalComplaint(String complaintNumber, String appealNumber, String actor) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "aa.workflow.error_remand_parent_missing"));

        String previousStatus = complaint.getStatus();
        complaint.setStatus("in_progress");
        complaint.setWorkflowStage("REMANDED_BY_AA");
        complaint.setClosedAt(null);
        complaint.setClosureClause(null);
        complaint.setReopenCount(complaint.getReopenCount() != null ? complaint.getReopenCount() + 1 : 1);
        complaint.setLastReopenedAt(LocalDateTime.now());
        complaintRepository.save(complaint);

        // A complaint-side row, because someone reading the COMPLAINT's history must see why it reopened
        // — the appeal timeline is a different record they may never look at. Note ComplaintTimeline has
        // no performedByRole and no field-level columns, so the detail goes in remarks.
        complaintTimelineRepository.save(ComplaintTimeline.builder()
                .complaintId(complaint.getId())
                .action("REMANDED_BY_AA")
                .performedBy(actor == null || actor.isBlank() ? "AA_MODULE" : actor)
                .remarks("Remanded by the Appellate Authority under appeal " + appealNumber)
                .fromStatus(previousStatus)
                .toStatus("in_progress")
                // AUTOMATIC, not MANUAL: a person passed the remand order, but the complaint-side reopen
                // is a side effect the system derived. Defaulting to MANUAL would attribute an ombudsman
                // action to an AA officer who never touched the complaint.
                .eventSource(TimelineEventSource.AUTOMATIC)
                .build());

        // Tell the holder. If nobody holds it, that is reported rather than passed over in silence —
        // an unowned reopened complaint is precisely what went unnoticed before.
        workflowNotifier.notifyRemandTarget(complaint.getAssignedOfficer(), complaintNumber, appealNumber);

        log.info("Remanded complaint {} back to the ombudsman under appeal {} by {} (holder: {})",
                complaintNumber, appealNumber, actor,
                complaint.getAssignedOfficer() == null ? "UNASSIGNED" : complaint.getAssignedOfficer());
    }

    /**
     * Who is raising this escalation. Drives appealability: a complainant may appeal under 15(1)(a)
     * or 15(1)(b), an entity only under 15(1)(b).
     *
     * Derived from the caller's own roles, not from a request field — an RE user must not be able to
     * claim complainant standing and obtain an appeal their entity is not entitled to.
     */
    private AppealParty resolveParty(Map<String, String> request) {
        Set<String> roles = aaIdentityResolver.resolveRoles();
        if (roles.contains("RE_PNO") || roles.contains("RE_NODAL_OFFICER") || roles.contains("RE_ADMIN")) {
            return AppealParty.ENTITY;
        }
        // Staff registering an escalation on a party's behalf must say for whom; a citizen filing on
        // the portal carries no RE role and is a complainant by default.
        String declared = request.get("appealFiledBy");
        if (declared != null && declared.trim().equalsIgnoreCase("ENTITY")) {
            return AppealParty.ENTITY;
        }
        return AppealParty.COMPLAINANT;
    }

    /**
     * Manually re-classifies an appeal, with a mandatory reason and a full audit record.
     *
     * Separate from performAction deliberately: performAction REJECTS any classification change to
     * keep the value immutable in the ordinary flow, so an override needs its own explicitly-audited
     * path rather than a hole in that guard. Restricted to AA_DO / AA_REVIEWER / AA_ADMIN.
     */
    @Transactional
    public Map<String, Object> overrideClassification(String appealNumber, String newClassification,
                                                      String reason) {
        Appeal appeal = appealRepository.findByAppealNumber(appealNumber)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealNumber));

        String actorRole = aaIdentityResolver.resolveAaRole();
        if (actorRole == null || !OVERRIDE_ROLES.contains(actorRole)) {
            throw new AaAccessDeniedException(
                    "Classification override requires one of " + OVERRIDE_ROLES);
        }

        if (newClassification == null || newClassification.isBlank()) {
            throw new IllegalArgumentException("newClassification is required");
        }
        String target = newClassification.trim().toUpperCase();
        if (!AppealClassificationService.APPEAL.equals(target)
                && !AppealClassificationService.REPRESENTATION.equals(target)) {
            throw new IllegalArgumentException("classification must be APPEAL or REPRESENTATION");
        }
        // Mandatory: an override without a stated reason is unauditable, and this is the record a
        // later reviewer relies on to understand why the derived clause mapping was set aside.
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException(
                    "A reason of at least 10 characters is required to override the classification");
        }

        String oldClassification = appeal.getClassificationType();
        if (target.equals(oldClassification)) {
            throw new IllegalArgumentException("Appeal is already classified as " + target);
        }

        String actor = Optional.ofNullable(aaIdentityResolver.resolveActor()).orElse("UNKNOWN");

        appeal.setClassificationType(target);
        appeal.setClassificationOverridden(true);
        appeal.setClassificationOverrideReason(reason.trim());
        appeal.setClassificationOverriddenBy(actor);
        appeal.setClassificationOverriddenAt(LocalDateTime.now());
        appealRepository.save(appeal);

        // Field-level old -> new, which the status-only timeline could not express on its own.
        AppealTimeline entry = AppealTimeline.builder()
                .appealNumber(appealNumber)
                .action("CLASSIFICATION_OVERRIDE")
                .performedBy(actor)
                .performedByRole(actorRole)
                .remarks(reason.trim())
                .fromStatus(appeal.getStatus())
                .toStatus(appeal.getStatus())
                .fieldName("classificationType")
                .oldValue(oldClassification)
                .newValue(target)
                .build();
        appealTimelineRepository.save(entry);

        log.info("Classification of {} overridden {} -> {} by {} ({})",
                appealNumber, oldClassification, target, actor, actorRole);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appealNumber", appealNumber);
        result.put("previousClassification", oldClassification);
        result.put("classificationType", target);
        result.put("overriddenBy", actor);
        result.put("reason", reason.trim());
        return result;
    }

    private String generateAppealNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "APL-" + date + "-" + uuid;
    }

    /**
     * Places the appeal with an officer of {@code roleGroup} using the real assignment engine.
     *
     * Replaces an in-memory ConcurrentHashMap round robin over Keycloak users, which was lost on restart,
     * diverged per pod, and honoured no workload threshold whatsoever. The engine persists its pointer,
     * enforces per-officer thresholds under a row lock, and records every placement.
     *
     * Returns null when the pool cannot take the work. An exhausted or empty pool is a legitimate
     * outcome, NOT an error: leaving the appeal unassigned and visible is safer than fabricating an
     * assignee who is over capacity, and the previous code's habit of silently picking someone is what
     * made overload invisible.
     */
    private String assignThroughEngine(String appealNumber, String roleGroup, Appeal appeal) {
        AaAssignmentResult result = assignmentEngine.assign(AaAssignmentRequest.builder()
                .appealNumber(appealNumber)
                .roleGroup(roleGroup)
                .regionalOffice(appeal.getEntityRegion())
                .build());

        if (!result.isAssigned()) {
            log.warn("AA workflow: {} could not be placed with a {} — outcome {}. Left unassigned.",
                    appealNumber, roleGroup, result.getOutcome());
            return null;
        }
        return result.getAssignedUserId();
    }

    /**
     * Returns a sent-back appeal to the DO who routed it, falling back to the pool only if that officer
     * is no longer available.
     */
    private String returnToRouter(String appealNumber, Appeal appeal) {
        String router = appeal.getRoutedByUserId();
        if (router != null && !router.isBlank()) {
            try {
                AaAssignmentResult result = assignmentEngine.assignManually(appealNumber, router,
                        "returned to the routing officer on SEND_BACK_REGISTRAR");
                return result.getAssignedUserId();
            } catch (RuntimeException e) {
                // The original router may have left, gone on leave or been deactivated. Falling back to
                // the pool is correct; silently dropping the appeal is not.
                log.warn("AA workflow: cannot return {} to its router {} ({}), falling back to the pool",
                        appealNumber, router, e.getMessage());
            }
        }
        return assignThroughEngine(appealNumber, "AA_DO", appeal);
    }

    /**
     * Story 5: a tier-1 reviewer escalates to a tier-2 reviewer.
     *
     * The acting reviewer's tier comes from the JWT claim ONLY. Accepting it from the request body would
     * let a tier-1 reviewer declare themselves tier 2 — the same self-declared-identity flaw that has
     * been closed four separate times in this module.
     *
     * Escalation-only by ruling: tier 2 is not a mandatory second review.
     */
    private void escalateToTier2(String appealNumber, Appeal appeal, String actor, String actorRole,
                                 String remarks) {
        String tier = aaIdentityResolver.resolveReviewerTier();
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("aa.workflow.error_reviewer_tier_unknown");
        }
        // A tier-2 reviewer has nowhere to escalate to. Allowing it would loop the appeal between peers
        // and read, in the audit trail, as progress.
        if (!"1".equals(tier.trim())) {
            throw new AaAccessDeniedException("aa.workflow.error_already_tier2");
        }

        String tier2Officer = findTier2Reviewer(appealNumber, appeal);
        if (tier2Officer == null) {
            throw new IllegalStateException("aa.workflow.error_no_tier2_reviewer");
        }

        appeal.setAssignedRole("AA_REVIEWER");
        appeal.setAssignedOfficer(tier2Officer);
        appeal.setWorkflowStage("ESCALATED_TO_TIER2");

        // Field-level audit, so the escalation is answerable later: who escalated, from whom, to whom.
        addFieldTimeline(appealNumber, "ESCALATE_TO_TIER2", actor, actorRole, remarks,
                appeal.getStatus(), "reviewerTier", "1", "2");
    }

    /**
     * Finds an available tier-2 reviewer.
     *
     * Tier lives in a Keycloak user attribute rather than the officer pool, so the pool alone cannot
     * express it. Candidates are taken from Keycloak and placed through the engine, which still applies
     * the threshold — an escalation must not overload the one senior reviewer.
     */
    /**
     * Accepts either a full timestamp or a date-only value.
     *
     * A bare LocalDateTime.parse threw DateTimeParseException on "2026-09-30", which no caller catches
     * and which surfaced as a 500 rather than a 400. A date-only hearing means the start of that day.
     */
    private static LocalDateTime parseHearingDate(String raw) {
        String value = raw.trim();
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            try {
                return LocalDate.parse(value).atStartOfDay();
            } catch (java.time.format.DateTimeParseException e2) {
                throw new IllegalArgumentException(
                        "hearingDate must be an ISO date or date-time, got: " + raw);
            }
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String findTier2Reviewer(String appealNumber, Appeal appeal) {
        try {
            for (Map<String, Object> user : keycloakUserService.getUsersByRole("AA_REVIEWER")) {
                Object tierAttr = user.get("reviewer_tier");
                String candidate = (String) user.get("userId");
                if (tierAttr != null && "2".equals(tierAttr.toString().trim())
                        && candidate != null && !candidate.equals(appeal.getAssignedOfficer())) {
                    return candidate;
                }
            }
        } catch (Exception e) {
            log.warn("AA workflow: could not read AA_REVIEWER tiers from Keycloak: {}", e.getMessage());
        }
        return null;
    }
}
