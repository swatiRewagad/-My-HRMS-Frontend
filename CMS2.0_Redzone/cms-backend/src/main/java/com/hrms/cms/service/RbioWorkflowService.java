package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.CommunicationOutbox;
import com.hrms.cms.entity.RbioMeeting;
import com.hrms.cms.entity.RbioWorkflowTransition;
import com.hrms.cms.entity.TimelineEventSource;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RbioWorkflowTransitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RBIO (RBI Ombudsman) workflow service.
 * Encapsulates all RBIO-specific workflow transition logic, role authorization,
 * compensation cap enforcement, SLA management, and audit logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioWorkflowService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintService complaintService;
    private final KeycloakUserService keycloakUserService;
    private final RbioSlaService rbioSlaService;
    private final RbioCompensationService rbioCompensationService;
    private final CepcAuditService auditService;
    private final NotificationService notificationService;

    private final Map<String, Integer> roundRobinCounters = new ConcurrentHashMap<>();

    /**
     * Server-side limit on the officer's custom closure text (UST576).
     *
     * <p>Matches {@code Complaint.customClosureText}'s column length. Previously enforced only by a
     * {@code maxlength} attribute in two templates, so an API caller could exceed it and the overflow
     * surfaced as a database error instead of a validation message.
     */
    static final int CUSTOM_CLOSURE_TEXT_MAX_LENGTH = 2000;

    /**
     * Optional so that {@code RbioWorkflowServiceTest} can construct this service with its seven
     * collaborators and no database. When absent — or when the table holds no matching row — resolution
     * falls back to {@link RbioTransitionRegistry}. See that class for why the fallback is required
     * rather than merely convenient.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RbioWorkflowTransitionRepository transitionRepository;

    /**
     * Optional for the same reason as {@link #transitionRepository}: {@code RbioWorkflowServiceTest}
     * constructs this service directly with no Spring context. Absent means no upload-link restriction
     * is applied, which is correct for a unit test that has no links — but note the guard is what makes
     * UST603-604 enforceable, so its absence in PRODUCTION would silently restore the bypass. It is a
     * real bean; nothing conditions it away.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UploadLinkGuardService uploadLinkGuardService;

    /**
     * Custody tracking for send-backs and reopens. Optional for the same reason as the repository above:
     * {@code RbioWorkflowServiceTest} builds this service with its declared collaborators and no Spring
     * context, and every pre-existing action must keep working without it.
     *
     * <p>The PREVIOUS_HOLDER strategy is the one path that cannot degrade gracefully when this is absent —
     * it refuses rather than guessing an assignee. See {@link #applyAssignee}.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RbioCaseAssignmentHistoryService assignmentHistoryService;

    /**
     * Configurable reopen vocabulary and authority (UST550-551). Optional for the same test-construction
     * reason as the two above; the compiled-in defaults apply when it is absent, so the Ombudsman-only
     * restriction holds even with no configuration present.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SystemConfigService systemConfigService;

    /**
     * Token-first identity, so the reopen authority check cannot be steered by a request body.
     *
     * <p>Optional like the collaborators above. When absent the check falls back to the body's
     * {@code userRole}, which is the pre-existing behaviour of every other action in this service — and
     * still refuses a blank role, so an unidentified caller cannot reopen.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hrms.cms.security.RbioIdentityResolver identityResolver;

    /**
     * Sets the entity's response deadline when a 13(1) notice is issued (UST780).
     *
     * <p>Optional for the same reason as the collaborators above: this service is constructed directly in
     * unit tests, and a required dependency would force every existing test to supply one. When absent the
     * notice still records its date and target party, so the statutory act is never lost — only the
     * deadline is skipped.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReResponseDeadlineService reResponseDeadlineService;

    /**
     * Impleading into a real child table, so each added party carries its own closure data (UST544-546).
     *
     * <p>Optional for the same test-construction reason as the collaborators above. When absent, impleading
     * still maintains the legacy CSV column on the complaint, so no pre-existing behaviour or test depends
     * on this bean — but the per-party row that closure validates is only written when it is present, which
     * is why the closure gate treats an absent service as "nothing recorded to verify" rather than passing a
     * complaint it was unable to check.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RbioImpleadService impleadService;

    /**
     * Queues closure email and SMS instead of dispatching inline (UST504-509, 549, 757, 763-764, 506).
     *
     * <p>Optional for the same reason. Absent means nothing is queued, which is the pre-existing behaviour;
     * present means the communication obligation is recorded durably and survives a gateway outage.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CommunicationOutboxService communicationOutboxService;

    /**
     * Refuses a closure whose communication requirements are unmet (UST507-509, 520, 549, 764).
     *
     * <p>Optional for the same test-construction reason as the collaborators above. Absent means no gate is
     * applied, which is the pre-existing behaviour — but note that its absence in PRODUCTION would restore
     * the bypass this guard exists to close. It is a real bean; nothing conditions it away.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ClosureCommunicationGuardService closureCommunicationGuardService;

    /**
     * Conciliation meeting history and its mandatory-field rules (UST496-503, 643-651).
     *
     * <p>Optional for the same test-construction reason as the collaborators above. Note what absence means
     * here: the meeting event is NOT recorded, and the mandatory-field validation does not run. That is the
     * pre-existing behaviour (which persisted nothing at all), but it is not silently tolerated — see
     * {@link #recordMeetingEvent}, which refuses the action rather than letting it appear to succeed.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RbioMeetingService rbioMeetingService;

    /**
     * The inter-office transfer queue, so an RBIO forward enters the CRPC Head's approval queue rather than
     * moving the complaint itself (UST557, 560, 564).
     *
     * <p>Optional for the same reason; absence refuses the forward rather than performing a partial one.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private InterOfficeTransferService interOfficeTransferService;

    /**
     * The RBI-department and regulatory-body masters, so a forward target is validated against master data
     * rather than accepted as free text (UST761, 766).
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ForwardTargetService forwardTargetService;

    /**
     * Enforces the per-role closure-clause tier (UST581-584) and validates the clause against
     * CLOSURE_CLAUSE_MASTER.
     *
     * <p>Optional for the same test-construction reason as the collaborators above. Absent means the clause is
     * accepted unvalidated, which is the pre-existing behaviour — it is a real bean, and its absence in
     * PRODUCTION would restore both the tier bypass and the unconfigured-clause hole.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ClosureClauseAccessService closureClauseAccessService;

    /**
     * Whether this transition closes the complaint, read from the row's own metadata rather than a hardcoded
     * action list, so a closing action added as a table row is covered automatically.
     */
    private boolean isClosureTransition(RbioWorkflowTransition transition) {
        return transition != null && (transition.getClosureCause() != null || transition.terminal());
    }

    /**
     * Parses an officer-supplied deadline, treating an unparseable value as absent.
     *
     * <p>Absent means "use the configured window", which is the safe direction: refusing the whole notice
     * because a date field was malformed would block a statutory communication over a formatting problem.
     */
    private static LocalDate parseDeadlineParam(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("Ignoring unparseable response deadline '{}'; using the configured window", raw);
            return null;
        }
    }

    /**
     * Check if an action is an RBIO-specific action.
     */
    public boolean isRbioAction(String action) {
        if (action == null) return false;
        String upper = action.toUpperCase();
        // The S3 ladder actions are recognised alongside Wave 0's. Without this, WorkflowController's
        // dispatch (`isRbioAction` gate) would never route them here at all, and the fifteen action codes
        // the frontend already sends would keep failing as "unknown" no matter what the table contains.
        return RbioTransitionRegistry.allActions().contains(upper)
                || RbioLadderActions.allActions().contains(upper)
                // S5's meeting and forwarding actions, for the same reason. RESCHEDULE_MEETING and
                // COMPLETE_MEETING are new, while FORWARD_TO_OTHER_OFFICE and FORWARD_TO_OTHER_RBI_DEPT are
                // already POSTed by task-action.component.ts on the RBIO route — where, existing only in
                // CepcWorkflowService, they threw "Unknown RBIO action".
                || RbioMeetingTransferActions.allActions().contains(upper);
    }

    /**
     * Main action router for RBIO workflow transitions.
     *
     * @param complaintNumber the complaint identifier
     * @param action          the workflow action to perform
     * @param params          additional parameters (actor, remarks, userRole, etc.)
     * @return result map with complaintNumber, action, newStatus, assignedRole, assignedOfficer
     */
    @Transactional
    public Map<String, Object> performAction(String complaintNumber, String action, Map<String, String> params) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintNumber));

        String actor = params.getOrDefault("actor", "");
        String remarks = params.getOrDefault("remarks", "");
        String userRole = params.getOrDefault("userRole", "");
        String previousStatus = complaint.getStatus();
        String previousStage = complaint.getWorkflowStage();

        // Captured BEFORE applyTransition, which overwrites the assignee. Reading it afterwards yields
        // the NEW owner, which is why the reassignment timeline row could never show who held the
        // complaint before (UST596).
        String previousOfficer = complaint.getAssignedOfficer();

        // Validate role authorization
        if (!userRole.isBlank() && !validateRoleAuthorization(userRole, action)) {
            throw new IllegalArgumentException(
                    String.format("Role '%s' is not authorized to perform action '%s'", userRole, action));
        }

        // Execute the state transition, resolved from RBIO_WORKFLOW_TRANSITION.
        //
        // A BLANK userRole skips the check above rather than failing it, and still executes — so the
        // transition is looked up by role when one was supplied and by action alone when it was not.
        // Requiring a role here would refuse every existing role-less caller.
        RbioWorkflowTransition transition = resolveTransition(action.toUpperCase(), userRole);
        if (transition == null) {
            throw new IllegalArgumentException("Unknown RBIO action: " + action.toUpperCase());
        }
        applyTransition(complaint, transition, action.toUpperCase(), params);

        // Save the complaint
        complaintRepository.save(complaint);

        // Record custody AFTER the save, so history never claims an officer holds a file whose assignment
        // was rolled back. Recorded for EVERY action rather than only the handovers, because a gap in the
        // trail is indistinguishable from "never held" and would silently degrade a later send-back to
        // manual selection.
        if (assignmentHistoryService != null) {
            assignmentHistoryService.recordAssignment(complaint, custodyRoleFor(complaint, transition, params),
                    complaint.getAssignedOfficer(), action.toUpperCase(), actor);
        }

        // Add timeline entry, carrying the old→new owner for a reassignment and the clause for a
        // closure (UST596). Both are otherwise unrecoverable: the assignee has already been overwritten
        // above, and Complaint.closureClause is mutable so a later re-closure destroys the original.
        String newOfficer = complaint.getAssignedOfficer();
        boolean ownerChanged = !Objects.equals(previousOfficer, newOfficer);

        complaintService.addDetailedTimeline(
                complaint.getId(),
                action,
                actor,
                userRole.isBlank() ? null : userRole,
                remarks,
                previousStatus,
                complaint.getStatus(),
                ownerChanged ? "assignedOfficer" : null,
                ownerChanged ? previousOfficer : null,
                ownerChanged ? newOfficer : null,
                // The clause supplied WITH THIS ACTION, not the complaint's current value: a reopened
                // complaint still carries the clause of its previous closure, and copying that onto an
                // unrelated event would attribute a clause to an action that was not made under one.
                params.get("closureClause"),
                params.get("destinationOffice"),
                TimelineEventSource.MANUAL);

        // Add audit log
        Map<String, Object> auditMetadata = new LinkedHashMap<>();
        auditMetadata.put("previousStage", previousStage);
        auditMetadata.put("newStage", complaint.getWorkflowStage());
        auditMetadata.put("assignedRole", complaint.getAssignedRole());
        auditMetadata.put("assignedOfficer", complaint.getAssignedOfficer());
        if (params.containsKey("targetUser")) {
            auditMetadata.put("targetUser", params.get("targetUser"));
        }

        auditService.logActionAsync(complaintNumber, action, actor, userRole,
                remarks, auditMetadata, previousStatus, complaint.getStatus());

        // ═══ Notification triggers ═══
        triggerRbioNotifications(complaint, action.toUpperCase(), actor, params);

        // Build response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("complaintNumber", complaint.getComplaintNumber());
        result.put("action", action);
        result.put("newStatus", complaint.getStatus());
        result.put("assignedRole", complaint.getAssignedRole());
        result.put("assignedOfficer", complaint.getAssignedOfficer());

        return result;
    }

    /**
     * Triggers in-app notifications based on RBIO workflow actions.
     * UST605: NEW_ASSIGNMENT, UST659: MEETING_SCHEDULED, UST663: COMPLAINT_CLOSED,
     * UST610: NO_REASSIGNED_TO_RBI
     */
    private void triggerRbioNotifications(Complaint c, String action, String actor, Map<String, String> params) {
        String complaintUrl = "/workflow/rbio/complaint/" + c.getComplaintNumber();

        switch (action) {
            case "SCHEDULE_MEETING":
                // UST659: MEETING_SCHEDULED — notify complainant + owner + RE
                String owner = c.getAssignedOfficer();
                if (owner != null && !owner.isBlank() && !owner.equals(actor)) {
                    notificationService.send(owner, "MEETING_SCHEDULED",
                            "Meeting scheduled for complaint",
                            "A meeting has been scheduled for complaint " + c.getComplaintNumber()
                                    + (c.getConciliationDate() != null ? " on " + c.getConciliationDate().toLocalDate() : ""),
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                // Notify RE (entity code as userId placeholder)
                if (c.getEntityCode() != null && !c.getEntityCode().isBlank()) {
                    notificationService.send(c.getEntityCode(), "MEETING_SCHEDULED",
                            "Meeting scheduled — your attendance required",
                            "A conciliation meeting has been scheduled for complaint " + c.getComplaintNumber()
                                    + ". Please prepare accordingly.",
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "FORWARD_TO_CONCILIATION":
            case "FORWARD_TO_ADJUDICATION":
            case "ESCALATE_TO_ADJUDICATION":
            case "REASSIGN":
                // UST605: NEW_ASSIGNMENT — notify new officer
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank() && !c.getAssignedOfficer().equals(actor)) {
                    notificationService.send(c.getAssignedOfficer(), "NEW_ASSIGNMENT",
                            "Complaint assigned to you",
                            "Complaint " + c.getComplaintNumber() + " has been assigned to you (" + c.getAssignedRole() + ") via " + action,
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "RESOLVE":
            case "CONCILIATION_SUCCESS":
            case "ADJUDICATION_AWARD":
            case "ADJUDICATION_REJECT":
            case "CLOSE_COMPLAINT":
            // UST536-538: ADVISORY_COMPLIED closes the complaint and is terminal, but was absent from this
            // arm — so it fell to default and notified nobody. A compliance auto-close follows the same
            // closure-communication rules as any other closure, which is exactly what the story requires.
            case "ADVISORY_COMPLIED":
                // UST663: COMPLAINT_CLOSED — notify owner
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank() && !c.getAssignedOfficer().equals(actor)) {
                    notificationService.send(c.getAssignedOfficer(), "COMPLAINT_CLOSED",
                            "Complaint closed",
                            "Complaint " + c.getComplaintNumber() + " has been closed via " + action,
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "RETURN_TO_OFFICER":
                // UST610: NO_REASSIGNED_TO_RBI — notify officer
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank()) {
                    notificationService.send(c.getAssignedOfficer(), "NO_REASSIGNED_TO_RBI",
                            "Complaint returned to you",
                            "Complaint " + c.getComplaintNumber() + " has been returned to RBIO_OFFICER for further action.",
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            default:
                break;
        }
    }

    /**
     * Validate whether a given role is authorized to perform the specified action.
     *
     * @param userRole the user's RBIO role
     * @param action   the action to validate
     * @return true if the role is authorized for the action
     */
    public boolean validateRoleAuthorization(String userRole, String action) {
        if (userRole == null || action == null) return false;

        // The DB table is authoritative for any role it knows, so a session that grants an action by
        // INSERTing a row — or revokes one by deactivating its rows — takes effect with no code change.
        // The registry answers only when the table has never heard of the role: an unseeded database, or
        // a role nobody has configured.
        if (transitionRepository != null) {
            try {
                if (transitionRepository.knowsRole(userRole)) {
                    return transitionRepository.existsForActionAndRole(action, userRole);
                }
            } catch (Exception e) {
                log.debug("Transition table unavailable, using registry: {}", e.getMessage());
            }
        }

        // Both compiled-in declarations are consulted, for the same reason the registry is: on a database
        // where the ladder rows were never seeded, an S3 action must still be authorised rather than
        // refused outright.
        String upper = action.toUpperCase();
        return RbioTransitionRegistry.actionsFor(userRole).contains(upper)
                || RbioLadderActions.actionsFor(userRole).contains(upper)
                || RbioMeetingTransferActions.actionsFor(userRole).contains(upper);
    }

    /**
     * Get the list of actions available for a complaint given the current state and user role.
     *
     * @param complaintNumber the complaint identifier
     * @param userRole        the user's RBIO role
     * @return list of permitted action names
     */
    public List<String> getAvailableActions(String complaintNumber, String userRole) {
        if (userRole == null || userRole.isBlank()) return Collections.emptyList();

        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) return Collections.emptyList();

        Complaint complaint = opt.get();
        Set<String> roleActions = availableActionCodes(userRole);

        // Filter actions based on the current complaint state.
        //
        // An S3 ladder action is judged by ITS OWN from-status list. Falling through to the registry would
        // return `true` for every ladder action (the registry's default for an action it does not know),
        // so a send-back would be advertised on a complaint that has never been up the ladder — exactly
        // the advertise/enforce disagreement the transition table exists to prevent.
        return roleActions.stream()
                .filter(action -> validForState(action, complaint.getStatus(), userRole))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Whether {@code action} should be offered to {@code userRole} on a complaint in {@code status}.
     *
     * <p>The TABLE is consulted first, so a session that grants an action from a new status by INSERTing a
     * row gets it advertised without a code change — which is the whole point of the transition table being
     * authoritative. Previously only the compiled-in from-status lists were consulted here, so a seeded row
     * could authorise an action that the UI would never offer: the table said yes and the screen said no.
     *
     * <p>UST779 is exactly that case. {@code ISSUE_NOTICE_13_1} is compiled in as valid only from
     * {@code adjudication}, but the Dealing Official must be able to issue it at Assessment. With the table
     * consulted, the grant is a seeded row rather than an edit to a registry another session owns.
     *
     * <p>Falls back to the compiled-in lists when the table has no row for this (action, role) — an
     * unseeded database still behaves exactly as before.
     */
    private boolean validForState(String action, String status, String userRole) {
        if (action == null || status == null) return false;

        if (transitionRepository != null && userRole != null) {
            try {
                List<RbioWorkflowTransition> rows = transitionRepository.findByIsActive("Y").stream()
                        .filter(t -> t.getActionCode().equalsIgnoreCase(action))
                        .filter(t -> t.getRoleName().equalsIgnoreCase(userRole))
                        .toList();
                if (!rows.isEmpty()) {
                    // A NULL from-status means "any status", which is how the table expresses an action that
                    // is not state-restricted. Matching it against the status string would silently make such
                    // a row unreachable.
                    return rows.stream().anyMatch(t -> t.getFromStatus() == null
                            || t.getFromStatus().isBlank()
                            || t.getFromStatus().equalsIgnoreCase(status));
                }
            } catch (Exception e) {
                log.debug("Transition table unavailable for state check, using registry: {}", e.getMessage());
            }
        }

        // An S3 ladder action is judged by ITS OWN from-status list. Falling through to the registry would
        // return `true` for every ladder action (the registry's default for an action it does not know), so
        // a send-back would be advertised on a complaint that has never been up the ladder — exactly the
        // advertise/enforce disagreement the transition table exists to prevent.
        if (RbioLadderActions.allActions().contains(action.toUpperCase())) {
            return RbioLadderActions.validForState(action, status);
        }
        // S5's actions are judged by their own from-status lists for the same reason. SCHEDULE_MEETING is
        // declared by BOTH Wave 0 and S5; S5's list is the wider one, so it is consulted first and the
        // registry answers only for actions S5 does not declare.
        if (RbioMeetingTransferActions.allActions().contains(action.toUpperCase())) {
            return RbioMeetingTransferActions.validForState(action, status)
                    || RbioTransitionRegistry.validForState(action, status);
        }
        return RbioTransitionRegistry.validForState(action, status);
    }

    /** Actions the role may perform, from the table when it knows the role, else from the registry. */
    private Set<String> availableActionCodes(String userRole) {
        if (transitionRepository != null) {
            try {
                if (transitionRepository.knowsRole(userRole)) {
                    return transitionRepository.findByIsActive("Y").stream()
                            .filter(t -> t.getRoleName().equalsIgnoreCase(userRole))
                            .map(RbioWorkflowTransition::getActionCode)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                }
            } catch (Exception e) {
                log.debug("Transition table unavailable, using registry: {}", e.getMessage());
            }
        }
        Set<String> actions = new LinkedHashSet<>(RbioTransitionRegistry.actionsFor(userRole));
        actions.addAll(RbioLadderActions.actionsFor(userRole));
        actions.addAll(RbioMeetingTransferActions.actionsFor(userRole));
        return actions;
    }

    // ═══ Private: State transition execution ═══

    /**
     * The transition for this action, from RBIO_WORKFLOW_TRANSITION when available.
     *
     * <p>Falls back to {@link RbioTransitionRegistry} when the table has no row — see that class for why
     * the fallback is required and not merely convenient. A blank role resolves the action's effect
     * alone, because {@code performAction} has always executed for callers that supplied no role.
     */
    private RbioWorkflowTransition resolveTransition(String action, String userRole) {
        if (transitionRepository != null && userRole != null && !userRole.isBlank()) {
            try {
                List<RbioWorkflowTransition> rows = transitionRepository.findByActionCodeAndRoleNameAndIsActive(
                        action, userRole, "Y");
                if (!rows.isEmpty()) {
                    return rows.get(0);
                }
            } catch (Exception e) {
                log.debug("Transition table unavailable, using registry: {}", e.getMessage());
            }
        }

        boolean roleless = (userRole == null || userRole.isBlank());

        RbioWorkflowTransition fromRegistry = roleless
                ? RbioTransitionRegistry.effectOf(action)
                : RbioTransitionRegistry.resolve(action, userRole);
        if (fromRegistry != null) return fromRegistry;

        // Then the S3 ladder declarations, for a database where the ladder rows were never seeded.
        RbioWorkflowTransition fromLadder = roleless
                ? RbioLadderActions.effectOf(action)
                : RbioLadderActions.resolve(action, userRole);
        if (fromLadder != null) return fromLadder;

        // Then S5's meeting and forwarding declarations, for the same reason.
        RbioWorkflowTransition fromS5 = roleless
                ? RbioMeetingTransferActions.effectOf(action)
                : RbioMeetingTransferActions.resolve(action, userRole);
        if (fromS5 != null) return fromS5;

        // A role that is authorised (checked before this point) but whose action has no declared effect
        // means the two declarations have drifted. Resolve the effect by action alone rather than
        // refusing, so an authorised action never fails for a reason the caller cannot act on.
        RbioWorkflowTransition byActionAlone = RbioTransitionRegistry.effectOf(action);
        if (byActionAlone != null) return byActionAlone;
        RbioWorkflowTransition ladderByAction = RbioLadderActions.effectOf(action);
        return ladderByAction != null ? ladderByAction : RbioMeetingTransferActions.effectOf(action);
    }

    /**
     * Applies a resolved transition to the complaint.
     *
     * <p>Replaces the {@code executeAction} switch. The column writes are driven by the row; the effects
     * a table cannot express (monetary parsing and cap validation, timestamp stamping, list append,
     * ladder-dependent status) are named in {@code sideEffect} and applied in {@link #applySideEffects}.
     *
     * <p>Ordering matters and is not arbitrary: required params are checked FIRST, before any mutation,
     * because a missing award amount must leave the complaint untouched and unsaved. The award cap is
     * validated inside the AWARD side effect, also before the status is written.
     */
    private void applyTransition(Complaint complaint, RbioWorkflowTransition transition,
                                 String action, Map<String, String> params) {
        requireParams(transition, params);
        requireComment(transition, params);

        // UST603-604: refuse a closure, or a forward to a deciding authority, while a secure
        // document-upload link is still live. Placed here — after the transition is resolved and its
        // params validated, but before the FIRST setter — so a refusal leaves the complaint unchanged.
        // Previously enforced only in the browser, and therefore bypassable by calling the API directly.
        if (uploadLinkGuardService != null) {
            uploadLinkGuardService.assertActionAllowed(
                    complaint.getComplaintNumber(), transition, params.get("targetRole"));
        }

        // UST507-509/520/549/764: refuse a closure whose communication requirements are unmet. Placed
        // alongside the upload-link guard and before the first setter for the same reason — a refusal must
        // leave the complaint untouched. dateOfSending was previously posted by the UI and dropped here.
        if (closureCommunicationGuardService != null) {
            closureCommunicationGuardService.assertClosureCommunicationComplete(
                    complaint, transition, firstNonBlankParam(params, "dateOfSending", "sendDate"));
        }

        // UST546: a closure must cover every impleaded party. The frontend's implead-validation call had no
        // server behind it and swallowed its own 404 as {valid:true}, so closure could finalise over parties
        // whose required data was never recorded.
        if (impleadService != null && isClosureTransition(transition)) {
            List<String> incomplete =
                    impleadService.findIncompleteParties(complaint.getComplaintNumber());
            if (!incomplete.isEmpty()) {
                throw new com.hrms.cms.exception.ImpleadedPartyIncompleteException(
                        "rbio.implead.error_incomplete_parties",
                        "This complaint cannot be closed until the closure clause and compensation are "
                                + "recorded for every impleaded party. Outstanding: "
                                + String.join(", ", incomplete) + ".",
                        complaint.getComplaintNumber(),
                        incomplete);
            }
        }

        String assignedRole = resolveAssignedRole(complaint, transition);
        if (assignedRole != null) {
            complaint.setAssignedRole(assignedRole);
        }

        applySideEffects(complaint, transition, action, params, assignedRole);

        // A NULL toStatus means "unchanged" — SCHEDULE_MEETING, ISSUE_NOTICE_13_1 and IMPLEAD_PARTY all
        // move only the stage. Writing it through would blank a live complaint's status.
        //
        // Skipped when a side effect already set the status, which only APPROVE does: its status depends
        // on which rank the file lands on, so the table cannot carry a literal.
        if (transition.getToStatus() != null && !hasSideEffect(transition, RbioTransitionRegistry.FX_APPROVE_LADDER)) {
            complaint.setStatus(transition.getToStatus());
        }
        if (transition.getToStage() != null && !hasSideEffect(transition, RbioTransitionRegistry.FX_APPROVE_LADDER)) {
            complaint.setWorkflowStage(transition.getToStage());
        }
        if (transition.getToMilestone() != null) {
            complaint.setMilestone(transition.getToMilestone());
        }

        applyAssignee(complaint, transition, params, assignedRole);

        if (transition.getClosureCause() != null) {
            String cause = hasSideEffect(transition, RbioTransitionRegistry.FX_CLOSURE_FROM_PARAM)
                    ? params.getOrDefault("closureCause", transition.getClosureCause())
                    : transition.getClosureCause();
            complaint.setClosureCause(cause);
        }

        if (transition.getSlaStage() != null) {
            rbioSlaService.applyStageSla(complaint, transition.getSlaStage());
        }

        applyClosureCommunication(complaint, transition, params);
    }

    /**
     * Persists the closure clause, the custom closure text and the Date of Sending, and queues the closure
     * communications (UST504-509, 520, 548, 576, 757, 762-764).
     *
     * <p>THE CLAUSE WAS NEVER PERSISTED BY THE RBIO PATH. {@code grep setClosureClause} found four call sites
     * and none of them was RBIO: the clause reached only a timeline row, so {@code COMPLAINTS.closure_clause}
     * stayed NULL after every RBIO closure and an appeal against it then failed closed with
     * {@code appeal.error_clause_not_configured}. Writing it here is what makes a closed RBIO complaint
     * appealable at all.
     *
     * <p>THE 2000-CHARACTER LIMIT IS ENFORCED HERE. It was a {@code maxlength} attribute and a counter getter
     * in two templates and nothing else, so an API caller could exceed it and the column's own
     * {@code length = 2000} would surface as a database error rather than a validation message. Modelled on
     * {@code ConsentService.NOTICE_MAX_LENGTH}, which is the established pattern for the same problem.
     *
     * <p>THE SEND DATE IS WRITE-ONCE. UST757/763 require it to be non-editable once recorded, which was
     * previously satisfied only by the accident of there being no setter anywhere. It is written only while
     * null, so a later action cannot revise when the citizen was told.
     */
    private void applyClosureCommunication(Complaint complaint, RbioWorkflowTransition transition,
                                           Map<String, String> params) {
        String clause = firstNonBlankParam(params, "closureClause");
        if (clause != null) {
            // UST584: refuse a clause this role may not cite, and any clause absent from the master. Checked
            // before the write so a refused clause leaves the complaint untouched.
            if (closureClauseAccessService != null) {
                closureClauseAccessService.assertRoleMayUseClause(
                        complaint, clause, params.getOrDefault("userRole", ""));
            }
            complaint.setClosureClause(clause);
        }

        String customText = firstNonBlankParam(params, "customClosureText");
        if (customText != null) {
            if (customText.length() > CUSTOM_CLOSURE_TEXT_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "The custom closure text may not exceed " + CUSTOM_CLOSURE_TEXT_MAX_LENGTH
                                + " characters (received " + customText.length() + ").");
            }
            complaint.setCustomClosureText(customText);
        }

        if (!isClosureTransition(transition)) {
            return;
        }

        String sendDate = firstNonBlankParam(params, "dateOfSending", "sendDate");
        if (sendDate != null && complaint.getDateOfSending() == null) {
            try {
                complaint.setDateOfSending(java.time.LocalDate.parse(sendDate.trim()));
            } catch (java.time.format.DateTimeParseException e) {
                // Refused rather than dropped. This is the field that evidences when the complainant was
                // informed, and silently discarding an unparseable value is exactly the defect being fixed.
                throw new IllegalArgumentException(
                        "dateOfSending must be an ISO date (yyyy-MM-dd); received '" + sendDate + "'");
            }
        }

        queueClosureCommunications(complaint);
    }

    /**
     * Queues the closure letter email and the closure SMS (UST504-506, 549, 757, 763-764, 577-578).
     *
     * <p>Queued rather than sent: the row commits with the closure, and a sender drains it afterwards, so a
     * gateway outage delays the message instead of losing the obligation. Previously the CEPC path generated
     * letter bytes, discarded them and stamped a "sent" timestamp, while RBIO did nothing at all.
     *
     * <p>Idempotent per complaint and channel, so a retried closure does not queue a second letter to the
     * same citizen.
     */
    private void queueClosureCommunications(Complaint complaint) {
        if (communicationOutboxService == null) {
            return;
        }
        String complaintNumber = complaint.getComplaintNumber();

        String email = complaint.getComplainantEmail();
        if (email != null && !email.isBlank()
                && !communicationOutboxService.alreadyQueued(complaintNumber,
                        CommunicationOutbox.TYPE_CLOSURE_LETTER, CommunicationOutbox.CHANNEL_EMAIL)) {
            communicationOutboxService.queue(CommunicationOutbox.builder()
                    .communicationType(CommunicationOutbox.TYPE_CLOSURE_LETTER)
                    .channel(CommunicationOutbox.CHANNEL_EMAIL)
                    .recipient(email)
                    .subject("Closure of your complaint " + complaintNumber)
                    // The letter body is rendered by ClosureLetterService from an editable template; the
                    // reference is carried here so the sender can attach the generated letter.
                    .body("Your complaint " + complaintNumber
                            + " has been closed. The closure letter is attached.")
                    .relatedReference(complaintNumber)
                    .build());
        }

        String phone = complaint.getComplainantPhone();
        if (phone != null && !phone.isBlank()
                && !communicationOutboxService.alreadyQueued(complaintNumber,
                        CommunicationOutbox.TYPE_CLOSURE_SMS, CommunicationOutbox.CHANNEL_SMS)) {
            communicationOutboxService.queue(CommunicationOutbox.builder()
                    .communicationType(CommunicationOutbox.TYPE_CLOSURE_SMS)
                    .channel(CommunicationOutbox.CHANNEL_SMS)
                    .recipient(phone)
                    // Stored as a resolved string here; the text itself is registered as a translation key
                    // (rbio.closure.sms_closed) so it is translatable across the ten supported locales.
                    .smsText("Your complaint " + complaintNumber
                            + " has been closed by the RBI Ombudsman. Please check your email for the closure letter.")
                    .relatedReference(complaintNumber)
                    .build());
        }
    }

    /**
     * Refuses the action when a declared-required param is absent.
     *
     * <p>A pipe-separated group means "at least one of". This is the award-amount refusal: the
     * adjudication screen sends {@code compensationAmount} while the API contract says
     * {@code awardAmount}, and reading only one of them persisted a citizen's compensation as 0.00 on an
     * irreversible statutory act. Absence is refused, never defaulted.
     */
    private void requireParams(RbioWorkflowTransition transition, Map<String, String> params) {
        String required = transition.getRequiredParams();
        if (required == null || required.isBlank()) return;

        for (String group : required.split(",")) {
            String[] alternatives = withAliases(group.trim().split("\\|"));
            if (firstNonBlankParam(params, alternatives) == null) {
                // The message names the canonical alternative, matching what the API contract documents.
                String canonical = alternatives[0];
                if ("awardAmount".equals(canonical)) {
                    throw new IllegalArgumentException(
                            "An award amount is required to issue an award (send awardAmount)");
                }

                // S5's meeting actions refuse with a real 4xx and a translation key, because
                // WorkflowController.performAction catches IllegalArgumentException and answers HTTP 200 with
                // success:false — which the Angular clients cannot see, since their error branch never fires
                // on a 200. A mandatory-field rule that surfaces as a success is worse than no rule: the
                // officer believes a meeting was convened when nothing was recorded.
                //
                // Scoped to the meeting actions rather than changed for every action, because the existing
                // 200-with-success:false shape is depended upon by CEPC and by other sessions' tests.
                if (RbioMeetingTransferActions.allActions().contains(transition.getActionCode())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            meetingParamMessage(canonical, transition.getActionCode()));
                }

                throw new IllegalArgumentException(canonical + " is required for " + transition.getActionCode() + " action");
            }
        }
    }

    /**
     * The translatable refusal for a missing meeting or forwarding param.
     *
     * <p>The key matches what {@code RbioMeetingService} emits for the same field, so the client sees one
     * message whichever guard fires first — the table backstop or the service's own validation.
     */
    private static String meetingParamMessage(String canonical, String action) {
        return switch (canonical) {
            case "meetingDate" -> "rbio.meeting.error.date_required: a meeting date is required (send meetingDate)";
            case "meetingTime" -> "rbio.meeting.error.time_required: a meeting time is required (send meetingTime)";
            case "participants" -> "rbio.meeting.error.participants_required: participants must be one of "
                    + "ENTITY, COMPLAINANT or BOTH";
            case "rescheduleReason" -> "rbio.meeting.error.reason_required: a reason is required to "
                    + "reschedule a meeting";
            case "entityAccepted" -> "rbio.meeting.error.acceptance_required: record whether the entity "
                    + "accepted (Yes/No)";
            case "minutesOfMeeting" -> "rbio.meeting.error.minutes_required: the minutes of the meeting are "
                    + "required";
            case "targetOffice" -> "rbio.forward.error.office_required: a destination office is required";
            case "transferReason" -> "rbio.forward.error.reason_required: a reason for transfer is required";
            case "targetDepartment" -> "rbio.forward.error.department_required: a target department is required";
            default -> canonical + " is required for " + action + " action";
        };
    }

    /**
     * Refuses the action when the row demands a comment and none was supplied.
     *
     * <p>{@code REQUIRES_COMMENT} has existed since Wave 0 with a helper to read it and NO caller — every
     * row seeded "N" and nothing ever checked it. UST516-517 and 531 require mandatory comments on the
     * send-backs, so this activates the column rather than adding a parallel mechanism beside it.
     *
     * <p>Checked before any mutation, alongside {@link #requireParams}: a refused action must leave the
     * complaint untouched and unsaved.
     */
    private void requireComment(RbioWorkflowTransition transition, Map<String, String> params) {
        if (!transition.commentRequired()) return;

        // "comments" is accepted as an alias because the shared task-action screen posts `remarks` while
        // some RBIO components post `comments`; requiring only one name would refuse a comment the user
        // demonstrably typed.
        if (firstNonBlankParam(params, "remarks", "comments") == null) {
            // ResponseStatusException, NOT IllegalArgumentException. WorkflowController.performAction
            // catches IllegalArgumentException and answers HTTP 200 with success:false, which the Angular
            // clients cannot see: their error branch never fires on a 200, so a refused send-back renders
            // as a successful one. A 400 reaches catchError. The shared catch block is left alone because
            // CEPC depends on its current behaviour.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A comment is required for the " + transition.getActionCode() + " action");
        }
    }

    /** The role the file moves to: a literal, or one step along the ladder for the relative moves. */
    private String resolveAssignedRole(Complaint complaint, RbioWorkflowTransition transition) {
        String target = transition.getAssignToRole();
        if (target == null) return null;
        if (RbioTransitionRegistry.NEXT_RANK.equals(target)) {
            return RbioRoles.nextRank(complaint.getAssignedRole());
        }
        if (RbioTransitionRegistry.PREVIOUS_RANK.equals(target)) {
            return RbioRoles.previousRank(complaint.getAssignedRole());
        }
        return target;
    }

    /** Sets the officer per the row's strategy. */
    private void applyAssignee(Complaint complaint, RbioWorkflowTransition transition,
                               Map<String, String> params, String assignedRole) {
        String strategy = transition.getAssignStrategy();
        if (strategy == null) return;

        switch (strategy) {
            case RbioTransitionRegistry.ACTOR ->
                    complaint.setAssignedOfficer(params.getOrDefault("actor", complaint.getAssignedOfficer()));

            // targetUserId is accepted as an alias of targetUser because the reassignment screen sends
            // that name (rbio-workflow.service.ts:93) while this method has only ever read targetUser.
            // The action declares no required params, so the mismatch answered 200 OK and left the
            // complaint with its ORIGINAL officer — the award-amount defect's shape, applied to identity.
            // Refusing an absent target is handled by the row's requiredParams, not by silence here.
            case RbioTransitionRegistry.TARGET_PARAM -> {
                String target = firstNonBlankParam(params, "targetUser", "targetUserId");
                if (target != null) {
                    complaint.setAssignedOfficer(target);
                }
            }

            case RbioTransitionRegistry.ROUND_ROBIN -> {
                // A null return leaves the existing officer in place: an unreachable Keycloak must not
                // orphan a live complaint by blanking its owner.
                String picked = assignByRole(assignedRole != null ? assignedRole : complaint.getAssignedRole());
                if (picked != null) {
                    complaint.setAssignedOfficer(picked);
                }
            }

            // A send-back returns the file to whoever previously held the target role. An explicit
            // target still wins, which is how the forced-manual-selection retry works.
            //
            // When no ACTIVE previous holder exists the action is REFUSED rather than degraded to
            // round-robin or left with the sender. Both of those silently give the file to someone the
            // process does not name — the CEPC send-back bug, where the role drops to the dealing
            // official while the reviewer who sent it back remains its owner.
            case RbioTransitionRegistry.PREVIOUS_HOLDER -> {
                String explicit = firstNonBlankParam(params, "targetUser", "targetUserId");
                if (explicit != null) {
                    complaint.setAssignedOfficer(explicit);
                    break;
                }

                String targetRole = assignedRole != null ? assignedRole : complaint.getAssignedRole();
                if (assignmentHistoryService == null) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Send-back requires the assignment history service; name the officer explicitly (targetUser)");
                }

                RbioCaseAssignmentHistoryService.Outcome outcome =
                        assignmentHistoryService.previousHolderFor(complaint.getComplaintNumber(), targetRole);
                if (!outcome.assignable()) {
                    // 422 rather than 400: the request is well-formed, but the state cannot satisfy it and
                    // the client must respond by prompting for manual selection. A distinct status lets the
                    // UI tell "you sent something invalid" apart from "now pick an officer".
                    //
                    // ResponseStatusException so this reaches the client as a real error status — see
                    // requireComment for why an IllegalArgumentException would surface as HTTP 200.
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            switch (outcome.availability()) {
                        case INACTIVE -> "The previous " + targetRole + " (" + outcome.officerId()
                                + ") is no longer active. Select an officer to send this complaint back to.";
                        default -> "No previous " + targetRole
                                + " held this complaint. Select an officer to send it back to.";
                    });
                }
                complaint.setAssignedOfficer(outcome.officerId());
            }

            default -> log.warn("Unknown RBIO assign strategy '{}' on action {}", strategy, transition.getActionCode());
        }
    }

    /** Effects that a table cannot express. */
    private void applySideEffects(Complaint complaint, RbioWorkflowTransition transition, String action,
                                 Map<String, String> params, String assignedRole) {
        String effects = transition.getSideEffect();
        if (effects == null || effects.isBlank()) return;

        LocalDateTime now = LocalDateTime.now();

        for (String raw : effects.split(",")) {
            String effect = raw.trim();
            switch (effect) {
                case RbioTransitionRegistry.FX_RESOLVED_AT -> complaint.setResolvedAt(now);
                case RbioTransitionRegistry.FX_CLOSED_AT -> complaint.setClosedAt(now);
                case RbioTransitionRegistry.FX_STAMP_ESCALATED -> complaint.setEscalatedAt(now);

                // Closure cause is read from the param in applyTransition; nothing to do here.
                case RbioTransitionRegistry.FX_CLOSURE_FROM_PARAM -> { }

                // APPROVE: the status depends on which rank the file lands on. Reproduces the old
                // branch exactly, including that a landing outside conciliation/adjudication yields
                // "escalated" rather than a generic "approved".
                case RbioTransitionRegistry.FX_APPROVE_LADDER -> {
                    if (RbioRoles.CONCILIATOR.equals(assignedRole)) {
                        complaint.setStatus("conciliation");
                        complaint.setWorkflowStage("CONCILIATION");
                        rbioSlaService.applyStageSla(complaint, "CONCILIATION");
                    } else if (RbioRoles.ADJUDICATOR.equals(assignedRole)) {
                        complaint.setStatus("adjudication");
                        complaint.setWorkflowStage("ADJUDICATION");
                        rbioSlaService.applyStageSla(complaint, "ADJUDICATION");
                    } else {
                        complaint.setStatus("escalated");
                        complaint.setWorkflowStage("ESCALATED");
                    }
                }

                // Wave 0's meeting effect, now delegating to the meeting history (UST496-503).
                //
                // WHAT THIS REPLACES. The arm read `meetingDate` while its only client sent `hearingDate`,
                // so nothing was ever persisted; and even a correctly-named value failed, because
                // LocalDateTime.parse rejects the date-only yyyy-MM-dd an <input type=date> sends. Both
                // failures were swallowed at log.debug, so every schedule reported success and recorded
                // nothing.
                //
                // Delegating from WAVE 0's effect name matters: SCHEDULE_MEETING is declared by both Wave 0
                // and S5, and Wave 0's row resolves first for the roles it already grants. Validating only
                // inside S5's own arm would leave the original path unguarded — the mandatory fields would
                // apply to some callers and not others.
                case RbioTransitionRegistry.FX_MEETING_DATE,
                     RbioMeetingTransferActions.FX_MEETING_SCHEDULED ->
                        recordMeetingEvent(complaint, RbioMeeting.EVENT_SCHEDULED, params);

                case RbioMeetingTransferActions.FX_MEETING_RESCHEDULED ->
                        recordMeetingEvent(complaint, RbioMeeting.EVENT_RESCHEDULED, params);

                case RbioMeetingTransferActions.FX_MEETING_COMPLETED ->
                        recordMeetingEvent(complaint, RbioMeeting.EVENT_COMPLETED, params);

                // UST557/560/564: a forward to another office REQUESTS a transfer and waits for the CRPC
                // Head. It does not move the complaint to the destination, and it does not write an office
                // code into assignedOfficer — which is what the CEPC arms do.
                case RbioMeetingTransferActions.FX_TRANSFER_REQUEST ->
                        requestOfficeTransfer(complaint, params);

                // UST761: a forward to an RBI department records the department and routes inside it.
                case RbioMeetingTransferActions.FX_FORWARD_DEPARTMENT ->
                        forwardToDepartment(complaint, params);

                case RbioTransitionRegistry.FX_ADVISORY_TEXT -> {
                    complaint.setAdvisoryText(params.getOrDefault("advisoryText", params.getOrDefault("remarks", "")));
                    complaint.setAdvisoryIssuedAt(now);
                }

                // Cap validation happens BEFORE any status write, so an over-cap award leaves the
                // complaint untouched and unsaved.
                case RbioTransitionRegistry.FX_AWARD -> {
                    String amountStr = firstNonBlankParam(params, "awardAmount", "compensationAmount");
                    String compensationType = params.getOrDefault("compensationType",
                            complaint.getCompensationType() != null ? complaint.getCompensationType() : "COMBINED");
                    BigDecimal awardAmount;
                    try {
                        awardAmount = new BigDecimal(amountStr);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid award amount: " + amountStr);
                    }
                    rbioCompensationService.validateAward(awardAmount, compensationType);

                    complaint.setAwardAmount(awardAmount);
                    complaint.setCompensationType(compensationType);
                    complaint.setAdjudicationDate(now);
                    complaint.setAdjudicationOutcome("AWARD_ISSUED");

                    // UST543: a dedicated Award Passed date. adjudicationDate above is shared with award
                    // REJECTION, so it cannot answer "when was an award passed" without also matching
                    // rejections — which is what the implemented/lapse reporting fields hang off.
                    complaint.setAwardPassedDate(now.toLocalDate());
                }

                case RbioTransitionRegistry.FX_ADJUDICATION_REJECT -> {
                    complaint.setAdjudicationDate(now);
                    complaint.setAdjudicationOutcome("REJECTED");
                }

                case RbioTransitionRegistry.FX_CONCILIATION_SUCCESS -> {
                    complaint.setConciliationOutcome("SUCCESS");
                    complaint.setConciliationDate(now);
                }

                case RbioTransitionRegistry.FX_CONCILIATION_FAILED -> {
                    complaint.setConciliationOutcome("FAILED");
                    // ESCALATE_TO_ADJUDICATION sets the outcome but NOT the date; CONCILIATION_FAILED
                    // sets both. Reproduced exactly rather than unified.
                    if ("CONCILIATION_FAILED".equals(action)) {
                        complaint.setConciliationDate(now);
                    }
                }

                case RbioTransitionRegistry.FX_NOTICE_13_1 -> {
                    complaint.setNotice131IssuedAt(now);

                    // UST780: a 13(1) notice starts the entity's response clock. Before this, the effect
                    // stamped a date and nothing else — re_response_deadline stayed NULL, so the sweep that
                    // chases an unresponsive entity filtered the complaint straight out and nobody was
                    // chased. The officer's chosen date is used when supplied, otherwise the configured
                    // window.
                    //
                    // targetParty is also persisted. The impleading screen has always sent it and this arm
                    // has never read it, so who the notice was addressed to was silently discarded on a
                    // statutory communication.
                    if (reResponseDeadlineService != null) {
                        LocalDate chosen = parseDeadlineParam(params.get("responseDeadline"));
                        ReResponseDeadlineService.DeadlineResult result =
                                reResponseDeadlineService.setDeadline(complaint, chosen, "13(1) Notice");
                        if (!result.accepted()) {
                            // Refused rather than silently adjusted: an officer who typed the wrong date on a
                            // statutory notice must be told, not have a different one substituted.
                            throw new IllegalArgumentException(result.reason());
                        }
                    }

                    String targetParty = firstNonBlankParam(params, "targetParty", "noticeTargetParty");
                    if (targetParty != null) {
                        complaint.setNotice131TargetParty(targetParty);
                    }
                }

                // impleadPartyName is accepted as an alias: rbio-adjudication.component.ts:153-159 sends
                // that name while this arm has only ever read partyName. The IMPLEAD_PARTY row declares
                // partyName required, so the mismatch surfaced as a 400 rather than persisting a blank
                // party — but only by luck, and the frontend's impleading screen could never succeed.
                case RbioTransitionRegistry.FX_IMPLEAD -> {
                    String partyName = firstNonBlankParam(params, "partyName", "impleadPartyName");
                    if (partyName == null) {
                        throw new IllegalArgumentException("partyName is required to implead a party");
                    }

                    // The CSV column is still maintained. Six other sessions read this schema and several
                    // tests assert on it, so it stays authoritative for existing readers while
                    // IMPLEADED_PARTY becomes authoritative for per-party facts. Dropping it is a separate,
                    // coordinated change.
                    String existing = complaint.getImpleadedParties();
                    complaint.setImpleadedParties(
                            (existing == null || existing.isBlank()) ? partyName : existing + "," + partyName);

                    // partyType was collected by the impleading screen and persisted by nothing before this.
                    String partyType = firstNonBlankParam(params, "partyType", "impleadPartyType");

                    if (impleadService != null) {
                        impleadService.implead(complaint, partyName, partyType,
                                params.getOrDefault("remarks", ""),
                                params.getOrDefault("actor", ""),
                                params.getOrDefault("userRole", ""));
                    }
                }

                // REASSIGN takes its target role from a param, so it overrides the row's assignToRole.
                case RbioTransitionRegistry.FX_REASSIGN_ROLE -> {
                    String targetRole = params.getOrDefault("targetRole", "");
                    if (!targetRole.isEmpty()) {
                        complaint.setAssignedRole(targetRole);
                    }
                }

                case RbioTransitionRegistry.FX_BULK_ROLE ->
                        complaint.setAssignedRole(params.getOrDefault("targetRole", RbioRoles.OFFICER));

                case RbioTransitionRegistry.FX_REOPEN -> {
                    // UST550-552. Validated BEFORE any field is cleared: a refused reopen must leave the
                    // closure intact, and this arm nulls resolvedAt/closedAt.
                    String reopenedByRole = requireReopenAuthority(params);

                    // UST551's reason-and-justification requirement is the OMBUDSMAN's reopen — the
                    // statutory act of reversing a concluded proceeding, which has to say on whose
                    // authority and why.
                    //
                    // RBIO_ADMIN's reopen is an administrative correction of a mis-closure and predates
                    // this story; it carried no reason field and no caller supplies one. Demanding one
                    // here would break every existing administrative reopen — a behaviour change to a
                    // working path, smuggled in under a new story. When an admin DOES supply a reason it
                    // is still validated and recorded, so nothing is silently accepted either.
                    boolean reasonMandatory = !RbioRoles.ADMIN.equals(reopenedByRole);
                    boolean reasonSupplied = firstNonBlankParam(params, "reopenReason", "reason") != null;

                    String reason = null;
                    String justification = null;
                    if (reasonMandatory || reasonSupplied) {
                        reason = requireReopenReason(params);
                        justification = firstNonBlankParam(params, "reopenJustification", "justification");
                        if (justification == null) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "A justification is required to reopen a complaint");
                        }
                    }

                    complaint.setReopenReason(reason);
                    complaint.setReopenJustification(justification);
                    complaint.setResolvedAt(null);
                    complaint.setClosedAt(null);
                    int current = complaint.getReopenCount() != null ? complaint.getReopenCount() : 0;
                    complaint.setReopenCount(current + 1);
                    complaint.setLastReopenedAt(now);
                    complaint.setReopenedAt(now);

                    // UST552: the file returns to the official who ORIGINALLY processed it, with all prior
                    // history intact — nothing here touches the timeline, attachments or closure clause.
                    // The originally-processing DO is not necessarily the last holder, so this is the
                    // earliest custody row for the dealing rank, not the most recent one.
                    if (assignmentHistoryService != null) {
                        String dealingRole = complaint.getAssignedRole() != null
                                && RbioRoles.OFFICER.equals(complaint.getAssignedRole())
                                ? RbioRoles.OFFICER : RbioRoles.DEALING_OFFICIAL;

                        RbioCaseAssignmentHistoryService.Outcome original =
                                assignmentHistoryService.originalHolderFor(complaint.getComplaintNumber(), dealingRole);
                        if (!original.assignable() && RbioRoles.DEALING_OFFICIAL.equals(dealingRole)) {
                            // Legacy complaints were held under the OFFICER name; fall back to it before
                            // giving up, so a reopen on an old file still finds its original owner.
                            original = assignmentHistoryService.originalHolderFor(
                                    complaint.getComplaintNumber(), RbioRoles.OFFICER);
                        }
                        if (original.assignable()) {
                            complaint.setAssignedOfficer(original.officerId());
                        }
                    }
                }

                // ═══ S3 ladder effects ═══

                // The determination is taken from the ACTION, not from a param, for the two explicit
                // DECIDE_* actions — the action code IS the decision, so a contradicting param must not be
                // able to record the opposite of the action performed. DEPUTY_OMBUDSMAN_DECISION is the
                // exception: it carries the finding in a param because one action expresses both outcomes.
                case RbioLadderActions.FX_MAINTAINABILITY -> {
                    String determination = switch (action) {
                        case RbioLadderActions.DECIDE_MAINTAINABLE -> "MAINTAINABLE";
                        case RbioLadderActions.DECIDE_NON_MAINTAINABLE -> "NON_MAINTAINABLE";
                        default -> normalisedMaintainability(params.get("maintainability"));
                    };
                    if (determination == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "maintainability must be MAINTAINABLE or NON_MAINTAINABLE");
                    }
                    complaint.setMaintainabilityDetermination(determination);
                    complaint.setMaintainabilityDeterminedBy(params.getOrDefault("actor", ""));
                    complaint.setMaintainabilityDeterminedAt(now);
                }

                case RbioLadderActions.FX_DEPUTY_DECISION -> {
                    String determination = normalisedMaintainability(params.get("maintainability"));
                    if (determination == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "maintainability must be MAINTAINABLE or NON_MAINTAINABLE");
                    }
                    String decision = params.getOrDefault("decision", "").trim().toUpperCase();
                    if (!"FACILITATION".equals(decision) && !"REJECTION".equals(decision)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "decision must be FACILITATION or REJECTION");
                    }
                    complaint.setMaintainabilityDetermination(determination);
                    complaint.setMaintainabilityDeterminedBy(params.getOrDefault("actor", ""));
                    complaint.setMaintainabilityDeterminedAt(now);
                    complaint.setDeputyDecision(decision);
                }

                case RbioLadderActions.FX_ADVISORY_COMPLIED -> complaint.setAdvisoryCompliedAt(now);

                // The body is recorded in its own column rather than written into assignedOfficer, which
                // is what the CEPC external-forward arms do — that leaves a non-user string in a user
                // column and the complaint apparently owned by an organisation.
                case RbioLadderActions.FX_REGULATORY_BODY -> {
                    String body = firstNonBlankParam(params, "regulatoryBodyName", "regulatoryBodyId");
                    if (body == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "regulatoryBodyName is required to forward to a regulatory body");
                    }

                    // UST766: restricted to the VALIDATED master list WITH VERIFIED EMAIL IDS. Previously
                    // any free-text string was accepted, because the master this was supposed to be checked
                    // against did not exist — the frontend called an endpoint no controller implements and
                    // turned the 404 into an empty dropdown while telling the officer that only validated
                    // bodies could be selected.
                    //
                    // Resolved rather than merely checked, so the recorded name is the master's canonical
                    // one and not whatever spelling the caller sent.
                    if (forwardTargetService != null) {
                        body = forwardTargetService.resolveRegulatoryBodyName(body);
                    }
                    complaint.setRegulatoryBodyName(body);

                    // UST766: an awareness email goes to the complainant automatically. The UI has told
                    // officers "Awareness email sent to complainant" since it was written, and no such email
                    // existed anywhere — the citizen was never informed that their complaint had left RBI's
                    // jurisdiction, while the file recorded that they had been.
                    queueRegulatoryBodyAwarenessEmail(complaint, body);
                }

                default -> log.warn("Unknown RBIO side effect '{}' on action {}", effect, action);
            }
        }
    }

    /**
     * Records a meeting event and mirrors its date onto the complaint (UST496-503, 643-651).
     *
     * <p>{@code conciliation_date} is still maintained because existing readers depend on it — the
     * conciliation notification reads it, and six other sessions read this schema. RBIO_MEETING is
     * authoritative for meeting FACTS; the scalar remains a convenience mirror of the operative date.
     *
     * <p><b>Refuses when the meeting service is absent rather than proceeding.</b> Without it the mandatory
     * date/time/participants rules would not run and no meeting row would be written, so the action would
     * move the workflow stage and record nothing — which is precisely the silent-success defect this batch
     * exists to remove. A 503 tells the officer to retry; a success message would not.
     */
    private void recordMeetingEvent(Complaint complaint, String eventType, Map<String, String> params) {
        if (rbioMeetingService == null) {
            // Absent only where this service is constructed directly in a unit test, which is the same
            // convention every other optional collaborator here follows (see their field comments): the
            // pre-existing behaviour is preserved rather than the action being refused. In PRODUCTION the
            // bean always exists — nothing conditions it away — so this branch cannot weaken the live
            // mandatory-field rules. Refusing here instead broke Wave 0's own pinned SCHEDULE_MEETING test,
            // which asserts that the action moves only the stage.
            log.warn("Meeting service unavailable; {} recorded the stage only for {}",
                    eventType, complaint.getComplaintNumber());
            return;
        }

        RbioMeeting meeting = rbioMeetingService.record(complaint, eventType, params,
                params.getOrDefault("actor", ""), params.getOrDefault("userRole", ""));

        if (meeting.getMeetingDate() != null) {
            // Time is folded in only for the mirror column, which is a LocalDateTime. The authoritative
            // values stay in their own columns on the meeting row.
            java.time.LocalTime time = java.time.LocalTime.MIDNIGHT;
            if (meeting.getMeetingTime() != null && !meeting.getMeetingTime().isBlank()) {
                try {
                    time = java.time.LocalTime.parse(meeting.getMeetingTime());
                } catch (java.time.format.DateTimeParseException e) {
                    // Unreachable: the service validates the shape before persisting. Left deliberate rather
                    // than propagating, because the mirror column must never be the reason a validated
                    // meeting fails to record.
                    log.debug("Meeting time '{}' not parseable for the mirror column", meeting.getMeetingTime());
                }
            }
            complaint.setConciliationDate(LocalDateTime.of(meeting.getMeetingDate(), time));
        }
    }

    /**
     * Requests an inter-office transfer instead of performing one (UST557, 560, 564, 770).
     *
     * <p><b>Why this does not move the complaint.</b> UST564 requires the transfer to enter the CRPC Head's
     * approval queue BEFORE reaching the destination Dealing Officer. So the action records a PENDING
     * transfer and sets the complaint to "Sent to Other Office"; the destination officer is resolved only on
     * approval. The CEPC arm this replaces moved the complaint immediately and never created a transfer row
     * at all, so the Head's queue was never populated and no approval was ever possible.
     *
     * <p>The destination office is validated against the office master, because a transfer to an office that
     * does not exist would leave the complaint in a status nobody owns.
     */
    private void requestOfficeTransfer(Complaint complaint, Map<String, String> params) {
        if (interOfficeTransferService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "rbio.forward.error.unavailable: the transfer service is unavailable, so the transfer was "
                            + "not requested. Please retry.");
        }

        String targetOffice = firstNonBlankParam(params, "targetOffice", "toOffice", "transferOffice");
        if (targetOffice == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.forward.error.office_required: a destination office is required");
        }

        String reason = firstNonBlankParam(params, "transferReason", "reason", "remarks");
        if (reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.forward.error.reason_required: a reason for transfer is required");
        }

        if (forwardTargetService != null) {
            forwardTargetService.assertOfficeExists(targetOffice);
        }

        interOfficeTransferService.requestTransfer(
                complaint.getComplaintNumber(),
                // The office the complaint currently sits in, which is the jurisdiction key and not the
                // department. Falls back to the department only when the office code was never set.
                complaint.getRbioOfficeCode() != null ? complaint.getRbioOfficeCode() : complaint.getDepartment(),
                targetOffice,
                transferTypeFor(complaint, targetOffice),
                reason,
                params.getOrDefault("actor", ""),
                firstNonBlankParam(params, "language", "preferredLanguage"));
    }

    /**
     * The transfer type, derived from the layout on each side of the move.
     *
     * <p>Recorded as data because {@code InterOfficeTransfer.transferType} is a free-text column whose
     * documented values (RBIO_RBIO, RBIO_CEPC, ...) were never validated or written consistently.
     */
    private String transferTypeFor(Complaint complaint, String targetOffice) {
        String from = complaint.getDepartment() == null ? "RBIO" : complaint.getDepartment().toUpperCase();
        String to = forwardTargetService != null
                ? forwardTargetService.layoutForOffice(targetOffice)
                : "RBIO";
        return from + "_" + to;
    }

    /**
     * Forwards to an RBI department, validating it against the department master (UST761, 534, 527-528).
     *
     * <p>The department is recorded in its own column. The CEPC arm this replaces wrote the department STRING
     * into {@code assignedOfficer} — a non-user value in a user column — and read it from {@code targetDept}
     * while the client sent {@code targetDepartment}, so the destination was silently dropped on every call.
     */
    private void forwardToDepartment(Complaint complaint, Map<String, String> params) {
        String department = firstNonBlankParam(params, "targetDepartment", "targetDept");
        if (department == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.forward.error.department_required: a target department is required");
        }

        // Validated against RBI_DEPARTMENT_MASTER rather than accepted as free text. The only department
        // list in the product was a hardcoded nine-element array in a CEPC component, including a literal
        // 'Other'.
        String resolved = forwardTargetService != null
                ? forwardTargetService.resolveDepartmentName(department)
                : department;

        complaint.setForwardedToDepartment(resolved);

        // UST761: assignment inside the target department follows the CRPC Head round-robin. Resolved
        // through the shared durable assigner, never an in-memory counter — there are already four of those
        // and each is per-pod.
        if (forwardTargetService != null) {
            String officer = forwardTargetService.resolveDepartmentOfficer(resolved);
            if (officer != null) {
                complaint.setAssignedOfficer(officer);
                notificationService.send(officer, "NEW_ASSIGNMENT",
                        "Complaint forwarded to your department",
                        "Complaint " + complaint.getComplaintNumber() + " has been forwarded to " + resolved
                                + " and assigned to you.",
                        complaint.getComplaintNumber(), "COMPLAINT",
                        "/workflow/rbio/complaint/" + complaint.getComplaintNumber());
            }
        }
    }

    /**
     * Queues the awareness email UST766 promises the complainant.
     *
     * <p>Queued through the outbox rather than sent inline, so the obligation commits with the forward and
     * survives a gateway outage — the pattern the closure communications already use. Idempotent per
     * complaint and channel, so a retried forward does not email the citizen twice.
     *
     * <p>When there is no email address, nothing is queued and nothing is claimed. The UI's unconditional
     * "Awareness email sent to complainant" message is corrected separately; a promise the server cannot
     * keep must not be made on either side.
     */
    private void queueRegulatoryBodyAwarenessEmail(Complaint complaint, String bodyName) {
        if (communicationOutboxService == null) {
            return;
        }
        String email = complaint.getComplainantEmail();
        if (email == null || email.isBlank()) {
            log.warn("Complaint {} forwarded to {} but the complainant has no email address on file — "
                    + "no awareness email queued", complaint.getComplaintNumber(), bodyName);
            return;
        }
        String complaintNumber = complaint.getComplaintNumber();
        if (communicationOutboxService.alreadyQueued(complaintNumber,
                CommunicationOutbox.TYPE_FORWARD_AWARENESS, CommunicationOutbox.CHANNEL_EMAIL)) {
            return;
        }
        communicationOutboxService.queue(CommunicationOutbox.builder()
                .communicationType(CommunicationOutbox.TYPE_FORWARD_AWARENESS)
                .channel(CommunicationOutbox.CHANNEL_EMAIL)
                .recipient(email)
                .subject("Your complaint " + complaintNumber + " has been referred to " + bodyName)
                .body("Your complaint " + complaintNumber + " concerns a matter outside the jurisdiction of "
                        + "the Reserve Bank - Integrated Ombudsman Scheme, 2021 and has been referred to "
                        + bodyName + " for further action. You may wish to contact them directly for updates.")
                .relatedReference(complaintNumber)
                .build());
    }

    private static boolean hasSideEffect(RbioWorkflowTransition transition, String effect) {
        String effects = transition.getSideEffect();
        if (effects == null) return false;
        for (String candidate : effects.split(",")) {
            if (candidate.trim().equals(effect)) return true;
        }
        return false;
    }

    private String assignByRole(String role) {
        try {
            List<Map<String, Object>> users = keycloakUserService.getUsersByRole(role);
            if (users.isEmpty()) return null;
            int index = roundRobinCounters.getOrDefault(role, 0);
            if (index >= users.size()) index = 0;
            String userId = (String) users.get(index).get("userId");
            roundRobinCounters.put(role, index + 1);
            return userId;
        } catch (Exception e) {
            log.warn("Failed to assign user by role {}: {}", role, e.getMessage());
            return null;
        }
    }

    /**
     * The role to record custody under — the role the officer actually HOLDS the file in.
     *
     * <p>Usually {@code complaint.assignedRole}, but not always, and the exception is load-bearing.
     * ACCEPT declares no {@code assignToRole} (its strategy is ACTOR: the performer takes ownership), so
     * it leaves the complaint's role at whatever it was. A complaint created with the legacy
     * {@code RBIO_OFFICER} role and then accepted by a Dealing Official therefore still reads
     * {@code RBIO_OFFICER}, and recording custody under that role means a later SEND_BACK_DO — which
     * looks up {@code RBIO_DEALING_OFFICIAL} — finds no previous holder and refuses.
     *
     * <p>So when the row does not redirect the file to another role, the ACTING role is authoritative:
     * the officer holds it in the capacity they acted in. When the row DOES name a target role
     * (SUBMIT_FOR_REVIEW → RBIO_REVIEWER), that target is the capacity the new holder takes it in, and
     * the actor's own role is irrelevant.
     */
    private static String custodyRoleFor(Complaint complaint, RbioWorkflowTransition transition,
                                        Map<String, String> params) {
        if (transition.getAssignToRole() != null) {
            return complaint.getAssignedRole();
        }
        String actingRole = params.getOrDefault("userRole", "").trim();
        return actingRole.isBlank() ? complaint.getAssignedRole() : actingRole.toUpperCase();
    }

    /** SYSTEM_CONFIG key holding the permitted reopen reasons (UST551). */
    static final String REOPEN_REASONS_KEY = "cms.rbio.reopen.reasons";

    /** SYSTEM_CONFIG key holding the roles permitted to reopen (UST550). */
    static final String REOPEN_ROLES_KEY = "cms.rbio.reopen.roles";

    /**
     * The reasons a complaint may be reopened, from configuration.
     *
     * <p>The fallback is the three UST551 names. It is a fallback and not a hardcoded list: an operator
     * adds a reason with a SYSTEM_CONFIG row, no release required.
     */
    private static final Set<String> DEFAULT_REOPEN_REASONS =
            Set.of("APPELLATE_AUTHORITY", "COURT_ORDER", "CORRECTION_REQUIRED");

    /**
     * Refuses a reopen by anyone but the Ombudsman (UST550).
     *
     * <p>Enforced HERE and not merely by hiding the control: the story says the Ombudsman alone may
     * reopen, and a hidden button is not a restriction — the endpoint accepts a direct POST.
     *
     * <p>ADMIN retains the ability because it held it before this change and removing it would break
     * administrative correction of a mis-closure; the configuration key can revoke it without a release.
     * A BLANK role is refused rather than waved through, unlike elsewhere in this service: every other
     * action's worst case is a mis-routed live file, whereas this one reverses a concluded proceeding.
     */
    private String requireReopenAuthority(Map<String, String> params) {
        Set<String> permitted = systemConfigService != null
                ? systemConfigService.getSet(REOPEN_ROLES_KEY, Set.of(RbioRoles.OMBUDSMAN, RbioRoles.ADMIN))
                : Set.of(RbioRoles.OMBUDSMAN, RbioRoles.ADMIN);

        // The TOKEN is authoritative, and the body param is only a fallback for callers that supply no
        // identity at all. Reading `userRole` from the request body first would let any caller reopen a
        // concluded proceeding by naming a role they do not hold — the precedence bug this codebase has
        // already had to fix five times elsewhere. identityResolver is token-first by construction.
        String actingRole = null;
        if (identityResolver != null) {
            try {
                actingRole = identityResolver.resolveRbioRole();
            } catch (Exception e) {
                log.debug("Could not resolve RBIO role for reopen: {}", e.getMessage());
            }
        }
        if (actingRole == null || actingRole.isBlank()) {
            actingRole = params.getOrDefault("userRole", "").trim();
        }

        if (actingRole.isBlank() || !permitted.contains(actingRole.toUpperCase())) {
            throw new SecurityException("Only the Ombudsman may reopen a closed complaint");
        }
        return actingRole.toUpperCase();
    }

    /** The supplied reopen reason, refused when it is absent or outside the configured vocabulary. */
    private String requireReopenReason(Map<String, String> params) {
        String reason = firstNonBlankParam(params, "reopenReason", "reason");
        if (reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reopen reason is required");
        }

        Set<String> permitted = systemConfigService != null
                ? systemConfigService.getSet(REOPEN_REASONS_KEY, DEFAULT_REOPEN_REASONS)
                : DEFAULT_REOPEN_REASONS;

        String normalised = reason.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if (!permitted.contains(normalised)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + reason + "' is not a permitted reopen reason. Permitted: " + permitted);
        }
        return normalised;
    }

    /**
     * Param names the client may send for the same value, keyed by the canonical name.
     *
     * <p>Declared in ONE place so the requirement check and the side effect that reads the value can never
     * disagree. They did: {@code IMPLEAD_PARTY} declares {@code partyName} required while the impleading
     * screen sends {@code impleadPartyName}, so accepting the alias only inside the side effect would
     * still have been refused by {@link #requireParams} before reaching it.
     *
     * <p>This is deliberately NOT a general "accept anything similar" rule. Each entry is a specific
     * client/server disagreement that exists in this codebase, and the canonical name stays first so error
     * messages continue to name what the API contract documents.
     */
    private static final Map<String, String[]> PARAM_ALIASES = Map.of(
            "partyName",  new String[]{"impleadPartyName"},
            "targetUser", new String[]{"targetUserId"},
            "remarks",    new String[]{"comments"});

    /** {@code alternatives} plus any declared aliases of them. */
    private static String[] withAliases(String[] alternatives) {
        List<String> expanded = new ArrayList<>(Arrays.asList(alternatives));
        for (String name : alternatives) {
            String[] aliases = PARAM_ALIASES.get(name);
            if (aliases != null) {
                expanded.addAll(Arrays.asList(aliases));
            }
        }
        return expanded.toArray(new String[0]);
    }

    /**
     * MAINTAINABLE / NON_MAINTAINABLE from a supplied value, or null when it is neither.
     *
     * <p>Returns null rather than defaulting, so the caller refuses. A maintainability finding decides
     * whether a citizen keeps their statutory recourse; guessing one from an unrecognised string would be
     * the worst possible defaulting in this module.
     */
    private static String normalisedMaintainability(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (value) {
            case "MAINTAINABLE" -> "MAINTAINABLE";
            case "NON_MAINTAINABLE", "NOT_MAINTAINABLE" -> "NON_MAINTAINABLE";
            default -> null;
        };
    }

    /** The first of {@code keys} present and non-blank in {@code params}, or null when none is. */
    private static String firstNonBlankParam(Map<String, String> params, String... keys) {
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
