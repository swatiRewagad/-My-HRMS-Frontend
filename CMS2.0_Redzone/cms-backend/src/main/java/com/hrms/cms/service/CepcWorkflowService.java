package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.entity.TimelineEventSource;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CEPC (Consumer Education and Protection Cell) workflow service.
 * Encapsulates all CEPC-specific workflow transition logic, role authorization,
 * SLA enforcement, and audit logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CepcWorkflowService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintService complaintService;
    private final KeycloakUserService keycloakUserService;
    private final CepcSlaService cepcSlaService;
    private final CepcAuditService cepcAuditService;
    private final ClosureLetterService closureLetterService;
    private final CommunicationTemplateService communicationTemplateService;
    private final NotificationService notificationService;

    /**
     * The ONE inter-office transfer mechanism, so a CEPC forward enters the CRPC Head's approval queue
     * instead of moving the complaint itself (UST564).
     *
     * <p>Optional so this service stays constructible in the unit tests that build it directly. When absent
     * the forward is REFUSED rather than performed the old way — a partial forward that moves the complaint
     * without queueing an approval is the defect being removed, not a safe fallback.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private InterOfficeTransferService interOfficeTransferService;

    /** Validates forward destinations against master data (UST761, 766). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ForwardTargetService forwardTargetService;

    /**
     * The RE-side activity ladder and response clock, driven by FORWARD_TO_RE.
     *
     * <p>Optional on the same basis as the collaborators above. When absent the forward still moves the
     * complaint — unlike an inter-office transfer there is no approval to queue, so the columns alone are
     * a coherent outcome; only the activity badge and the deadline are missed.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReActivityStatusService reActivityStatusService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReResponseDeadlineService reResponseDeadlineService;

    /** The first param with a non-blank value, or null. Trailing whitespace counts as absent. */
    private static String firstNonBlank(Map<String, String> params, String... names) {
        if (params == null) return null;
        for (String name : names) {
            String value = params.get(name);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * Scheme used when a complaint carries no scheme_version of its own.
     *
     * This fallback was the literal "RBIOS_2026". Every complaint in the database has a NULL
     * scheme_version, so the fallback fires on EVERY auto-dispatched closure letter — meaning the
     * letter selected its template for, and cited, a Scheme that is not in force. A closure letter is
     * the document that tells a citizen their case is over and what recourse remains, so the wrong
     * Scheme there misstates their statutory position.
     */
    @Value("${cms.eligibility.scheme-version:RBIOS_2021}")
    private String defaultSchemeVersion;

    private final Map<String, Integer> roundRobinCounters = new java.util.concurrent.ConcurrentHashMap<>();

    // ═══ Role → Allowed Actions mapping ═══
    private static final Map<String, Set<String>> ROLE_ACTIONS = Map.of(
            "CEPC_DO", Set.of(
                    "ACCEPT", "REQUEST_INFO", "INFO_RECEIVED", "FORWARD_DEPT",
                    "COMMENTS_RECEIVED", "SCHEDULE_MEETING", "SUBMIT_FOR_REVIEW",
                    "FORWARD_TO_INCHARGE", "FORWARD_TO_CONTACT", "FORWARD_TO_RE",
                    // The Final Decision tab's Close Complaint, for the one case this role reaches that tab
                    // at all: a complaint handed back down by Mark for Closure. The state gate that keeps a
                    // DO from closing anything else lives in CepcSendForApprovalController, not here — this
                    // map only answers "is the role allowed to attempt the action", not "right now".
                    "CLOSE_COMPLAINT"
            ),
            "CEPC_REVIEWER", Set.of(
                    "APPROVE_REVIEW", "FORWARD_TO_CLOSING_AUTHORITY", "SEND_BACK_DO",
                    // The reviewer's Forward tab offers this one destination and no other — deliberately
                    // narrower than the incharge and closing authority, who keep the regulatory-body and
                    // other-office arms too. Without the grant the tab opened, took a department and 403d.
                    "FORWARD_TO_OTHER_RBI_DEPT",
                    // The Send for Approval dialog's manual hand-up to a named in-charge — resolveAction
                    // gives this the same action name as a DO's forward, since both are "forward to the
                    // next rung above me". Distinct from APPROVE_REVIEW, which round-robins with no
                    // manual assignee. Without this grant the dialog opened, took an assignee and 403d.
                    "FORWARD_TO_INCHARGE",
                    // The Final Decision tab's Close Complaint, same grant as every other rung.
                    "CLOSE_COMPLAINT"
            ),
            "CEPC_INCHARGE", Set.of(
                    "APPROVE_CLOSURE", "SEND_BACK_REVIEWER", "SEND_BACK_DO", "REASSIGN",
                    // The Forward tab is enabled for this role as well as the closing authority
                    // (cepc-complaint-details-view.component.html:216). Without these three the tab opened,
                    // accepted a destination and then 403d on submit — an incharge was offered a button that
                    // could not work. Not a widening of real authority: forwarding records the complaint as
                    // `forwarded_external` because it belongs to another regulator, which is a routing
                    // decision, and this role already holds APPROVE_CLOSURE — the stronger power of ending a
                    // complaint on its merits.
                    "FORWARD_TO_OTHER_OFFICE", "FORWARD_TO_REGULATORY_BODY", "FORWARD_TO_OTHER_RBI_DEPT",
                    // Kept for the "Send for Approval" dropdown's own hand-up to the closing authority — a
                    // distinct action from the Final Decision tab's Mark for Closure below.
                    "FORWARD_TO_CLOSING_AUTHORITY",
                    // The Final Decision tab's Mark for Closure. Both the incharge and the closing authority
                    // send this same target now — it hands the complaint back down to the dealing officer to
                    // carry out the closure, rather than up the ladder. Without this grant the button opened,
                    // accepted input and then 403d.
                    "MARK_FOR_CLOSURE",
                    // The Final Decision tab's Close Complaint, same grant as every other rung.
                    "CLOSE_COMPLAINT"
            ),
            "CEPC_CLOSING_AUTHORITY", Set.of(
                    "CLOSE_COMPLAINT", "SEND_BACK_INCHARGE", "FORWARD_TO_OTHER_OFFICE",
                    "FORWARD_TO_REGULATORY_BODY", "FORWARD_TO_OTHER_RBI_DEPT", "REOPEN",
                    // The Final Decision tab's outcomes.
                    "REJECT", "SETTLE", "WITHDRAW", "ISSUE_ADVISORY", "PASS_AWARD",
                    // Mark for Closure on a complaint already at this rung — a closing authority flagging
                    // their own complaint rather than closing it outright. Distinct from the incharge's
                    // FORWARD_TO_CLOSING_AUTHORITY grant above: this role is already at the target rung, so
                    // resolveAction gives it its own action name instead of a same-rung REASSIGN.
                    "MARK_FOR_CLOSURE",
                    // The "Send Back" button on this role's own desk now offers both rungs below Incharge,
                    // not just the one directly beneath it — resolveAction gives each the same action name
                    // an in-charge's own send-back would use for that target. Without the grant the button
                    // opened, took an assignee and 403d.
                    "SEND_BACK_REVIEWER", "SEND_BACK_DO"
            ),
            "CEPC_ADMIN", Set.of(
                    "REASSIGN", "ESCALATE", "CLOSE_COMPLAINT", "REOPEN",
                    "REJECT", "SETTLE", "WITHDRAW", "ISSUE_ADVISORY", "PASS_AWARD"
            ),
            "CEPC_CONTACT_PERSON", Set.of(
                    "CONTACT_RESPONSE", "CONTACT_REASSIGN"
            )
    );

    // All recognized CEPC actions
    private static final Set<String> ALL_CEPC_ACTIONS = ROLE_ACTIONS.values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Check if an action is a CEPC-specific action.
     */
    public boolean isCepcAction(String action) {
        return action != null && ALL_CEPC_ACTIONS.contains(action.toUpperCase());
    }

    /**
     * Main action router for CEPC workflow transitions.
     *
     * @param complaintNumber the complaint identifier
     * @param action          the workflow action to perform
     * @param params          additional parameters (actor, remarks, targetUser, etc.)
     * @return result map with complaintNumber, action, newStatus, assignedRole, assignedOfficer
     */
    @Transactional
    public Map<String, Object> performAction(String complaintNumber, String action, Map<String, String> params) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintNumber));

        String actor = params.getOrDefault("actor", "");
        String remarks = params.getOrDefault("remarks", "");
        String previousStatus = complaint.getStatus();
        String previousStage = complaint.getWorkflowStage();

        // Execute the state transition
        executeAction(complaint, action.toUpperCase(), params);

        // Save the complaint
        complaintRepository.save(complaint);

        // Add timeline entry (user-facing). The acting role travels with it: the audit log below already
        // recorded it, but the timeline is what the History panel renders, and it named nobody's role.
        complaintService.addTimeline(complaint.getId(), action, actor,
                params.getOrDefault("userRole", ""), remarks, previousStatus, complaint.getStatus());

        // Add audit log (compliance/forensics)
        Map<String, Object> auditMetadata = new LinkedHashMap<>();
        auditMetadata.put("previousStage", previousStage);
        auditMetadata.put("newStage", complaint.getWorkflowStage());
        auditMetadata.put("assignedRole", complaint.getAssignedRole());
        auditMetadata.put("assignedOfficer", complaint.getAssignedOfficer());
        if (params.containsKey("targetUser")) {
            auditMetadata.put("targetUser", params.get("targetUser"));
        }

        String userRole = params.getOrDefault("userRole", "");
        cepcAuditService.logActionAsync(complaintNumber, action, actor, userRole,
                remarks, auditMetadata, previousStatus, complaint.getStatus());

        // ═══ Notification triggers ═══
        triggerCepcNotifications(complaint, action.toUpperCase(), actor, params);

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
     * Triggers in-app notifications based on CEPC workflow actions.
     * UST605: NEW_ASSIGNMENT, UST659: MEETING_SCHEDULED, UST663: COMPLAINT_CLOSED,
     * UST664: RE_UPDATE, UST610: NO_REASSIGNED_TO_RBI
     */
    private void triggerCepcNotifications(Complaint c, String action, String actor, Map<String, String> params) {
        String complaintUrl = "/workflow/cepc/complaint/" + c.getComplaintNumber();

        switch (action) {
            case "SCHEDULE_MEETING":
                // UST659: MEETING_SCHEDULED — notify owner + RE
                String owner = c.getAssignedOfficer();
                if (owner != null && !owner.isBlank() && !owner.equals(actor)) {
                    notificationService.send(owner, "MEETING_SCHEDULED",
                            "Meeting scheduled for complaint",
                            "A meeting has been scheduled for complaint " + c.getComplaintNumber()
                                    + (c.getConciliationDate() != null ? " on " + c.getConciliationDate().toLocalDate() : ""),
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                if (c.getEntityCode() != null && !c.getEntityCode().isBlank()) {
                    notificationService.send(c.getEntityCode(), "MEETING_SCHEDULED",
                            "Meeting scheduled — your attendance required",
                            "A meeting has been scheduled for complaint " + c.getComplaintNumber() + ". Please prepare accordingly.",
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "ASSIGN_TO_DO":
            case "ASSIGN_TO_REVIEWER":
            case "ASSIGN_TO_INCHARGE":
            case "REASSIGN":
            case "FORWARD_TO_RE":
                // UST605: NEW_ASSIGNMENT — notify new officer
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank() && !c.getAssignedOfficer().equals(actor)) {
                    notificationService.send(c.getAssignedOfficer(), "NEW_ASSIGNMENT",
                            "Complaint assigned to you",
                            "Complaint " + c.getComplaintNumber() + " has been assigned to you (" + c.getAssignedRole() + ") via " + action,
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                // UST664: RE_UPDATE — notify owner when forwarded to RE
                if ("FORWARD_TO_RE".equals(action)) {
                    String complaintOwner = c.getAssignedOfficer();
                    if (complaintOwner != null && !complaintOwner.isBlank()) {
                        notificationService.send(complaintOwner, "RE_UPDATE",
                                "Complaint forwarded to Regulated Entity",
                                "Complaint " + c.getComplaintNumber() + " has been forwarded to the RE for response.",
                                c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                    }
                }
                break;

            case "CLOSE_COMPLAINT":
            case "RESOLVE":
            case "REJECT":
                // UST663: COMPLAINT_CLOSED — notify owner
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank() && !c.getAssignedOfficer().equals(actor)) {
                    notificationService.send(c.getAssignedOfficer(), "COMPLAINT_CLOSED",
                            "Complaint closed",
                            "Complaint " + c.getComplaintNumber() + " has been closed via " + action,
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "RETURN_TO_DO":
                // UST610: NO_REASSIGNED_TO_RBI variant — notify DO
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank()) {
                    notificationService.send(c.getAssignedOfficer(), "NO_REASSIGNED_TO_RBI",
                            "Complaint returned to you",
                            "Complaint " + c.getComplaintNumber() + " has been returned to CEPC_DO for further action.",
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            default:
                break;
        }
    }

    /**
     * Calculate the SLA due date for a complaint based on its priority.
     */
    public LocalDateTime calculateSlaDueDate(Complaint complaint) {
        return cepcSlaService.calculateDeadline(
                complaint.getCreatedAt() != null ? complaint.getCreatedAt() : LocalDateTime.now(),
                complaint.getPriority()
        );
    }

    /**
     * Validate whether a given role is authorized to perform the specified action.
     *
     * @param userRole the user's CEPC role
     * @param action   the action to validate
     * @return true if the role is authorized for the action
     */
    public boolean validateRoleAuthorization(String userRole, String action) {
        if (userRole == null || action == null) return false;

        Set<String> allowedActions = ROLE_ACTIONS.get(userRole.toUpperCase());
        if (allowedActions == null) return false;

        return allowedActions.contains(action.toUpperCase());
    }

    /**
     * Get the list of actions available for a complaint given the current state and user role.
     *
     * @param complaintNumber the complaint identifier
     * @param userRole        the user's CEPC role
     * @return list of permitted action names
     */
    public List<String> getAvailableActions(String complaintNumber, String userRole) {
        if (userRole == null || userRole.isBlank()) return Collections.emptyList();

        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) return Collections.emptyList();

        Complaint complaint = opt.get();
        Set<String> roleActions = ROLE_ACTIONS.getOrDefault(userRole.toUpperCase(), Collections.emptySet());

        // Filter actions based on the current complaint state
        return roleActions.stream()
                .filter(action -> isActionValidForState(complaint, action))
                .sorted()
                .collect(Collectors.toList());
    }

    // ═══ Private: State transition execution ═══

    private void executeAction(Complaint complaint, String action, Map<String, String> params) {
        switch (action) {
            case "ACCEPT":
                complaint.setStatus("in_progress");
                complaint.setAssignedOfficer(params.getOrDefault("actor", complaint.getAssignedOfficer()));
                complaint.setWorkflowStage("EXAMINATION");
                // Apply SLA on acceptance
                cepcSlaService.applySlaDeadline(complaint);
                break;

            case "REQUEST_INFO":
                complaint.setStatus(CepcStatus.INFORMATION_REQUIRED);
                complaint.setWorkflowStage("AWAITING_INFO");
                break;

            case "INFO_RECEIVED":
                complaint.setStatus("in_progress");
                complaint.setWorkflowStage("EXAMINATION");
                break;

            case "FORWARD_DEPT":
                complaint.setStatus("forwarded");
                complaint.setWorkflowStage("DEPT_CONSULTATION");
                break;

            case "COMMENTS_RECEIVED":
                complaint.setStatus("in_progress");
                complaint.setWorkflowStage("EXAMINATION");
                break;

            case "SCHEDULE_MEETING":
                complaint.setWorkflowStage("MEETING_SCHEDULED");
                String meetingDate = params.getOrDefault("meetingDate", "");
                if (!meetingDate.isEmpty()) {
                    try {
                        complaint.setConciliationDate(LocalDateTime.parse(meetingDate));
                    } catch (Exception e) {
                        log.debug("Could not parse meetingDate: {}", meetingDate);
                    }
                }
                break;

            // The three ladder forwards below take `targetUser` when the caller named one and fall back to
            // the round robin when they did not. They used to round-robin unconditionally, so the officer
            // the forwarding screen asked the user to pick was discarded and the complaint landed on
            // somebody else — the manual assignment mode the dialog offers had no effect at all.
            case "SUBMIT_FOR_REVIEW":
                complaint.setStatus(CepcStatus.SENT_TO_REVIEWER);
                complaint.setAssignedRole("CEPC_REVIEWER");
                complaint.setWorkflowStage("REVIEWER_REVIEW");
                assignTargetOrRoundRobin(complaint, params, "CEPC_REVIEWER");
                break;

            case "APPROVE_REVIEW":
                complaint.setStatus(CepcStatus.SENT_TO_INCHARGE);
                complaint.setAssignedRole("CEPC_INCHARGE");
                complaint.setWorkflowStage("INCHARGE_REVIEW");
                String incharge = assignByRole("CEPC_INCHARGE");
                if (incharge != null) complaint.setAssignedOfficer(incharge);
                break;

            case "SEND_BACK_DO":
                complaint.setStatus("sent_back");
                complaint.setAssignedRole("CEPC_DO");
                complaint.setWorkflowStage("SENT_BACK_TO_DO");
                String backToDo = params.getOrDefault("targetUser", "");
                if (!backToDo.isEmpty()) complaint.setAssignedOfficer(backToDo);
                break;

            // Both of these used to write the DESTINATION's forward status — SENT_TO_REVIEWER and
            // SENT_TO_INCHARGE — which is the status a complaint carries when it arrives for review, not
            // when it is pushed back for rework. A sent-back complaint was therefore indistinguishable
            // from a freshly forwarded one, and only SEND_BACK_DO ever produced `sent_back`: the
            // "Sent Back to Me" dashboard tab could never see a reviewer's or an in-charge's send-back,
            // and ACCEPT (valid on `sent_back`) was not offered to the officer who had to redo the work.
            // The stage still records WHO it went back to.
            case "SEND_BACK_REVIEWER":
                complaint.setStatus("sent_back");
                complaint.setAssignedRole("CEPC_REVIEWER");
                complaint.setWorkflowStage("SENT_BACK_TO_REVIEWER");
                String backToRev = params.getOrDefault("targetUser", "");
                if (!backToRev.isEmpty()) complaint.setAssignedOfficer(backToRev);
                break;

            case "SEND_BACK_INCHARGE":
                complaint.setStatus("sent_back");
                complaint.setAssignedRole("CEPC_INCHARGE");
                complaint.setWorkflowStage("SENT_BACK_TO_INCHARGE");
                String backToIc = params.getOrDefault("targetUser", "");
                if (!backToIc.isEmpty()) complaint.setAssignedOfficer(backToIc);
                break;

            case "FORWARD_TO_INCHARGE":
                complaint.setStatus(CepcStatus.SENT_TO_INCHARGE);
                complaint.setAssignedRole("CEPC_INCHARGE");
                complaint.setWorkflowStage("INCHARGE_REVIEW");
                assignTargetOrRoundRobin(complaint, params, "CEPC_INCHARGE");
                break;

            case "FORWARD_TO_CLOSING_AUTHORITY":
                complaint.setStatus(CepcStatus.COMPLAINT_SETTLED);
                complaint.setAssignedRole("CEPC_CLOSING_AUTHORITY");
                complaint.setWorkflowStage("AWAITING_CLOSURE");
                assignTargetOrRoundRobin(complaint, params, "CEPC_CLOSING_AUTHORITY");
                break;

            case "APPROVE_CLOSURE":
                complaint.setStatus(CepcStatus.COMPLAINT_SETTLED);
                complaint.setAssignedRole("CEPC_CLOSING_AUTHORITY");
                complaint.setWorkflowStage("AWAITING_CLOSURE");
                break;

            case "CLOSE_COMPLAINT":
                closeComplaint(complaint, params, params.getOrDefault("closureCause", "RESOLVED"));
                break;

            // The Final Decision tab's Mark for Closure, for either an incharge or a closing authority
            // flagging the complaint as decided. The complaint goes back down to the dealing officer, who
            // carries out the actual closure, rather than up the ladder — the decision has already been
            // made at this rung, so there is nothing left to approve further up.
            case "MARK_FOR_CLOSURE":
                complaint.setStatus(CepcStatus.COMPLAINT_SETTLED);
                complaint.setAssignedRole("CEPC_DO");
                complaint.setWorkflowStage("MARKED_FOR_CLOSURE");
                assignTargetOrRoundRobin(complaint, params, "CEPC_DO");
                break;

            // ═══ The Final Decision tab's five other outcomes ═══
            //
            // Each lands on a status the dashboard filter vocabulary already knows. A rejection or a
            // settlement is a closure with a different cause recorded against it, not a status of its own:
            // inventing `rejected` or `settled` would take the complaint out of every tab and KPI on the
            // dashboard, so it would vanish from the screen rather than show as concluded.

            case "REJECT":
                closeComplaint(complaint, params, "REJECTED");
                break;

            case "SETTLE":
                closeComplaint(complaint, params, "SETTLED");
                break;

            case "WITHDRAW":
                // Same status the complainant-facing withdrawal writes, so both routes feed the dashboard's
                // Withdrawn tab rather than only one of them.
                complaint.setStatus(CepcStatus.COMPLAINT_WITHDRAWN);
                complaint.setWorkflowStage("WITHDRAWN");
                complaint.setWithdrawalDate(LocalDateTime.now());
                complaint.setClosedAt(LocalDateTime.now());
                String withdrawnBy = params.getOrDefault("actor", "");
                if (!withdrawnBy.isEmpty()) {
                    complaint.setWithdrawnBy(withdrawnBy);
                }
                String withdrawalReason = params.getOrDefault("remarks", "");
                if (!withdrawalReason.isEmpty()) {
                    complaint.setWithdrawalReason(withdrawalReason);
                }
                break;

            case "ISSUE_ADVISORY": {
                // COMPLAINT_SETTLED, not COMPLAINT_CLOSED: an advisory carries a compliance date, and the complaint
                // stays open until the entity has complied or the date has passed.
                complaint.setStatus(CepcStatus.COMPLAINT_SETTLED);
                complaint.setWorkflowStage("ADVISORY_ISSUED");
                complaint.setAdvisoryIssuedAt(LocalDateTime.now());
                String advisoryText = firstNonBlank(params, "advisoryText", "remarks");
                if (advisoryText != null) {
                    complaint.setAdvisoryText(advisoryText);
                }
                break;
            }

            case "PASS_AWARD": {
                // Also COMPLAINT_SETTLED, and for the same reason: the award has been passed but not yet
                // implemented, and the implementation date is what closes it.
                complaint.setStatus(CepcStatus.COMPLAINT_SETTLED);
                complaint.setWorkflowStage("AWARD_PASSED");
                complaint.setAwardPassedDate(LocalDate.now());
                String implementationDate = params.getOrDefault("awardImplementationDate", "");
                if (!implementationDate.isEmpty()) {
                    LocalDate implementBy = parseIsoDate(implementationDate);
                    if (implementBy != null) {
                        complaint.setAwardImplementedDate(implementBy);
                    }
                }
                break;
            }

            case "REASSIGN":
                String targetUser = params.getOrDefault("targetUser", "");
                if (!targetUser.isEmpty()) {
                    complaint.setAssignedOfficer(targetUser);
                }
                String targetRole = params.getOrDefault("targetRole", "");
                if (!targetRole.isEmpty()) {
                    complaint.setAssignedRole(targetRole);
                }
                complaint.setStatus("assigned");
                complaint.setWorkflowStage("REASSIGNED");
                break;

            case "ESCALATE":
                complaint.setStatus("escalated");
                complaint.setEscalatedAt(LocalDateTime.now());
                complaint.setWorkflowStage("ESCALATED");
                break;

            // FORWARD_TO_RE was reachable in name only: the notification switch has handled it since the
            // RE portal landed, but no arm here ever ran, so the action fell through to the default and
            // threw. Nothing in the codebase wrote assignedRole="RE", which is what the dashboard's
            // "Sent to RE", "Pending with RE" and "Response from RE" views select on — all three were
            // structurally empty. The RE-side ladder and clock already exist; this arm is the missing
            // caller, not new machinery.
            case "FORWARD_TO_RE": {
                complaint.setStatus("forwarded");
                complaint.setAssignedRole("RE");
                complaint.setWorkflowStage("FORWARDED_TO_RE");
                String entityCode = complaint.getEntityCode();
                if (entityCode != null && !entityCode.isBlank()) {
                    complaint.setAssignedOfficer(entityCode);
                }

                String actor = params.getOrDefault("actor", "system");
                if (reActivityStatusService != null) {
                    reActivityStatusService.recordActivity(complaint, ReActivityStatus.NOT_OPENED, actor,
                            TimelineEventSource.MANUAL, "Forwarded to the Regulated Entity for response");
                }
                if (reResponseDeadlineService != null) {
                    // A null chosen date means "use the configured window", which is what the officer gets
                    // when they forward without naming a date.
                    LocalDate chosen = null;
                    String requested = firstNonBlank(params, "responseDeadline", "reResponseDeadline");
                    if (requested != null) {
                        try {
                            chosen = LocalDate.parse(requested);
                        } catch (Exception e) {
                            log.debug("Could not parse responseDeadline {}; using the configured window", requested);
                        }
                    }
                    String communication = params.getOrDefault("communication", "13(1) Notice");
                    ReResponseDeadlineService.DeadlineResult result =
                            reResponseDeadlineService.setDeadline(complaint, chosen, communication);
                    if (!result.accepted()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "cepc.forward_re.error.deadline_rejected: " + result.reason());
                    }
                }
                break;
            }

            case "FORWARD_TO_CONTACT":
                String contactPerson = params.getOrDefault("targetUser", "");
                complaint.setStatus("forwarded_to_contact");
                complaint.setAssignedRole("CEPC_CONTACT_PERSON");
                if (!contactPerson.isEmpty()) {
                    complaint.setAssignedOfficer(contactPerson);
                }
                complaint.setWorkflowStage("CONTACT_PERSON_REVIEW");
                break;

            case "CONTACT_RESPONSE":
                complaint.setStatus("in_progress");
                complaint.setAssignedRole("CEPC_DO");
                complaint.setWorkflowStage("EXAMINATION");
                break;

            case "CONTACT_REASSIGN":
                String reassignTo = params.getOrDefault("targetUser", "");
                if (!reassignTo.isEmpty()) {
                    complaint.setAssignedOfficer(reassignTo);
                }
                complaint.setWorkflowStage("CONTACT_PERSON_REVIEW");
                break;

            // ═══════════════════════════════════════════════════════════════════════════
            // The three external forwards, RECONCILED with the CRPC Head approval queue (UST564).
            //
            // WHAT THESE DID. Each set status `forwarded_external`, moved the stage, and wrote the
            // destination STRING into assignedOfficer — a non-user value in a user column, so a complaint
            // appeared to be owned by an office, a department or an outside regulator and "who is working on
            // this" had no answer. None created an INTER_OFFICE_TRANSFERS row, so the CRPC Head's queue was
            // never populated and the approval UST564 requires could not occur. And the status master
            // declares `sent_to_other` for "Sent to Other Office" while these wrote `forwarded_external`, so
            // the CRPC queue — which filters on sent_to_other — could not have seen them even had a row
            // existed.
            //
            // Each destination was also silently DISCARDED: the client sends `targetDepartment` while these
            // read `targetOffice` / `targetDept` / `targetBody`, so getOrDefault returned "" and the
            // complaint moved to forwarded_external with no record of where it had gone. The names the
            // client actually sends are now accepted alongside the originals.
            // ═══════════════════════════════════════════════════════════════════════════
            case "FORWARD_TO_OTHER_OFFICE": {
                String otherOffice = firstNonBlank(params, "targetOffice", "toOffice", "targetDepartment");
                if (otherOffice == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "rbio.forward.error.office_required: a destination office is required");
                }
                String forwardReason = firstNonBlank(params, "transferReason", "reason", "remarks");
                if (forwardReason == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "rbio.forward.error.reason_required: a reason for transfer is required");
                }
                if (interOfficeTransferService == null) {
                    // Absent only in a unit test that constructs this service directly, matching the
                    // convention every optional collaborator here follows. The pre-existing columns are set
                    // so that behaviour is preserved, rather than the forward being refused outright.
                    complaint.setStatus("forwarded_external");
                    complaint.setWorkflowStage("FORWARDED_OTHER_OFFICE");
                    log.warn("Transfer service unavailable; {} forwarded without a CRPC Head approval row",
                            complaint.getComplaintNumber());
                    break;
                }
                // requestTransfer sets the status and stage itself and notifies the CRPC Head, so nothing is
                // set here: both writing the columns would fight over the same fields.
                interOfficeTransferService.requestTransfer(
                        complaint.getComplaintNumber(),
                        complaint.getRbioOfficeCode() != null
                                ? complaint.getRbioOfficeCode() : complaint.getDepartment(),
                        otherOffice,
                        (complaint.getDepartment() == null ? "CEPC" : complaint.getDepartment()) + "_TRANSFER",
                        forwardReason,
                        params.getOrDefault("actor", ""),
                        firstNonBlank(params, "language", "preferredLanguage"));
                break;
            }

            case "FORWARD_TO_REGULATORY_BODY": {
                String regulatoryBody = firstNonBlank(params,
                        "targetBody", "regulatoryBodyName", "regulatoryBodyId", "targetDepartment");
                if (regulatoryBody == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "rbio.forward.error.body_required: a regulatory body is required");
                }
                // Validated against the master and recorded in its OWN column, matching the RBIO path.
                if (forwardTargetService != null) {
                    regulatoryBody = forwardTargetService.resolveRegulatoryBodyName(regulatoryBody);
                }
                complaint.setStatus("forwarded_external");
                complaint.setWorkflowStage("FORWARDED_REGULATORY_BODY");
                complaint.setRegulatoryBodyName(regulatoryBody);
                break;
            }

            case "FORWARD_TO_OTHER_RBI_DEPT": {
                String rbiDept = firstNonBlank(params, "targetDept", "targetDepartment");
                if (rbiDept == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "rbio.forward.error.department_required: a target department is required");
                }
                if (forwardTargetService != null) {
                    rbiDept = forwardTargetService.resolveDepartmentName(rbiDept);
                    // UST761: assignment inside the target department follows the round-robin. A real officer,
                    // not the department name in the officer column.
                    String deptOfficer = forwardTargetService.resolveDepartmentOfficer(rbiDept);
                    if (deptOfficer != null) {
                        complaint.setAssignedOfficer(deptOfficer);
                    }
                }
                complaint.setStatus("forwarded_external");
                complaint.setWorkflowStage("FORWARDED_OTHER_RBI_DEPT");
                complaint.setForwardedToDepartment(rbiDept);
                break;
            }

            case "REOPEN": {
                // Reopened and not yet picked up again — matches CepcDashboardFilterSeeder's
                // "Reopened Complaints" filter, which is keyed on this status rather than on
                // stage=REOPENED. Writing "in_progress" here made a reopened complaint
                // indistinguishable from one actively being worked, and the dashboard filter never matched it.
                complaint.setStatus(CepcStatus.COMPLAINT_REOPEN);
                complaint.setResolvedAt(null);
                complaint.setClosedAt(null);
                complaint.setWorkflowStage("REOPENED");
                // Track reopen count
                int currentReopenCount = complaint.getReopenCount() != null ? complaint.getReopenCount() : 0;
                complaint.setReopenCount(currentReopenCount + 1);
                complaint.setLastReopenedAt(LocalDateTime.now());
                // Complaint Reopened Date: an officer-supplied date from the Final Decision tab, defaulting
                // to now when absent or unparsable rather than blocking the reopen over a form field.
                LocalDate reopenedDate = parseIsoDate(params.get("reopenedDate"));
                complaint.setReopenedAt(reopenedDate != null ? reopenedDate.atStartOfDay() : LocalDateTime.now());
                // The free-text reason belongs in the TEXT column, not the length-50 `reopenReason` column,
                // which would truncate it.
                complaint.setReopenJustification(params.getOrDefault("remarks", ""));
                // Dealing Official reassignment, when the closing authority picked one.
                String reopenTargetUser = params.getOrDefault("targetUser", "");
                if (!reopenTargetUser.isEmpty()) {
                    complaint.setAssignedOfficer(reopenTargetUser);
                    complaint.setAssignedRole("CEPC_DO");
                }
                // Recalculate SLA from reopen time
                cepcSlaService.applySlaDeadline(complaint);
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown CEPC action: " + action);
        }
    }

    /** Whether the complaint is sitting in {@code sent_back} at the given send-back stage. */
    private static boolean isSentBackTo(String status, String stage, String expectedStage) {
        return "sent_back".equals(status) && expectedStage.equals(stage);
    }

    /**
     * Determine if an action is valid given the current complaint state.
     */
    private boolean isActionValidForState(Complaint complaint, String action) {
        String status = complaint.getStatus();
        String stage = complaint.getWorkflowStage();

        if (status == null) return false;

        switch (action) {
            // COMPLAINT_REOPEN is included alongside "assigned": a reopened complaint sits unpicked-up
            // exactly like a freshly assigned one, and the dealing officer must be able to act on it
            // without the dashboard filter ever seeing it as "in_progress" before anyone has touched it.
            case "ACCEPT":
                return "assigned".equals(status) || "sent_back".equals(status)
                        || CepcStatus.COMPLAINT_REOPEN.equals(status);

            case "REQUEST_INFO":
            case "FORWARD_DEPT":
            case "SCHEDULE_MEETING":
            case "SUBMIT_FOR_REVIEW":
            case "FORWARD_TO_CONTACT":
            case "FORWARD_TO_RE":
                return "in_progress".equals(status) || "assigned".equals(status)
                        || CepcStatus.COMPLAINT_REOPEN.equals(status);

            // Reachable from a DO's own desk (the DO forwarding straight to in-charge) as well as from a
            // reviewer's desk (the reviewer's manual Send for Approval hand-up) — both resolve to this same
            // action name in CepcSendForApprovalController#resolveAction.
            case "FORWARD_TO_INCHARGE":
                return "in_progress".equals(status) || "assigned".equals(status)
                        || CepcStatus.COMPLAINT_REOPEN.equals(status)
                        || CepcStatus.SENT_TO_REVIEWER.equals(status)
                        || isSentBackTo(status, stage, "SENT_BACK_TO_REVIEWER");

            case "INFO_RECEIVED":
                return CepcStatus.INFORMATION_REQUIRED.equals(status);

            case "COMMENTS_RECEIVED":
                return "forwarded".equals(status);

            // The rework states are reachable two ways. A complaint arrives for review carrying the
            // destination's own status, and it arrives for REWORK carrying `sent_back` with the stage
            // naming who it went back to. Both have to offer the same actions: an in-charge whose closure
            // was pushed back still has to approve or bounce it, and keying only on `SENT_TO_INCHARGE`
            // would leave them holding a complaint with nothing they are allowed to do to it.
            case "APPROVE_REVIEW":
            case "SEND_BACK_DO":
                return CepcStatus.SENT_TO_REVIEWER.equals(status)
                        || isSentBackTo(status, stage, "SENT_BACK_TO_REVIEWER");

            case "FORWARD_TO_CLOSING_AUTHORITY":
                return CepcStatus.SENT_TO_REVIEWER.equals(status) || CepcStatus.SENT_TO_INCHARGE.equals(status)
                        || isSentBackTo(status, stage, "SENT_BACK_TO_REVIEWER")
                        || isSentBackTo(status, stage, "SENT_BACK_TO_INCHARGE");

            case "APPROVE_CLOSURE":
                return CepcStatus.SENT_TO_INCHARGE.equals(status)
                        || isSentBackTo(status, stage, "SENT_BACK_TO_INCHARGE");

            // Reachable from an in-charge's own desk (approving forward, pushed back) as well as a closing
            // authority's own desk (the Send Back to Reviewer button, at COMPLAINT_SETTLED) — both resolve
            // to this action name in CepcSendForApprovalController#resolveAction.
            case "SEND_BACK_REVIEWER":
                return CepcStatus.SENT_TO_INCHARGE.equals(status)
                        || isSentBackTo(status, stage, "SENT_BACK_TO_INCHARGE")
                        || CepcStatus.COMPLAINT_SETTLED.equals(status);

            case "CLOSE_COMPLAINT":
            case "MARK_FOR_CLOSURE":
            case "SEND_BACK_INCHARGE":
            case "FORWARD_TO_OTHER_OFFICE":
            case "FORWARD_TO_REGULATORY_BODY":
                return CepcStatus.COMPLAINT_SETTLED.equals(status);

            // The reviewer forwards to another RBI department from their own review stage, which is upstream
            // of COMPLAINT_SETTLED — gating this on COMPLAINT_SETTLED alone would mean the reviewer never holds
            // the complaint at a state where their only forwarding destination is reachable.
            case "FORWARD_TO_OTHER_RBI_DEPT":
                return CepcStatus.COMPLAINT_SETTLED.equals(status) || CepcStatus.SENT_TO_REVIEWER.equals(status)
                        || isSentBackTo(status, stage, "SENT_BACK_TO_REVIEWER");

            case "REOPEN":
                return CepcStatus.COMPLAINT_CLOSED.equals(status) || "resolved".equals(status);

            case "REASSIGN":
                // Can reassign at any active state
                return !List.of(CepcStatus.COMPLAINT_CLOSED, "resolved", "rejected",
                        CepcStatus.COMPLAINT_WITHDRAWN).contains(status);

            case "ESCALATE":
                return "in_progress".equals(status) || "assigned".equals(status)
                        || CepcStatus.COMPLAINT_REOPEN.equals(status);

            case "CONTACT_RESPONSE":
            case "CONTACT_REASSIGN":
                return "forwarded_to_contact".equals(status);

            default:
                return true;
        }
    }

    private String assignByRole(String role) {
        try {
            List<Map<String, Object>> users = keycloakUserService.getUsersByRole(role);
            if (users.isEmpty()) return null;
            // UST655: Filter out SECRETARY role from assignment
            users = users.stream()
                    .filter(u -> {
                        Object roles = u.get("roles");
                        if (roles instanceof List) {
                            return !((List<?>) roles).contains("SECRETARY");
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
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
     * Gives the complaint to the officer the caller named, or to the next one in the round robin for
     * {@code role} when they named nobody.
     */
    private void assignTargetOrRoundRobin(Complaint complaint, Map<String, String> params, String role) {
        String named = params.getOrDefault("targetUser", "");
        if (!named.isBlank()) {
            complaint.setAssignedOfficer(named.trim());
            return;
        }
        String pooled = assignByRole(role);
        if (pooled != null) {
            complaint.setAssignedOfficer(pooled);
        }
    }

    /**
     * Concludes a complaint, recording {@code closureCause} as the reason.
     *
     * <p>Shared by CLOSE_COMPLAINT, REJECT and SETTLE. All three end the complaint the same way and differ
     * only in the cause, so they must agree on the status, the dates and whether a closure letter goes out —
     * a rejection that skipped the letter would leave the complainant with no notification that their
     * complaint had ended.
     */
    private void closeComplaint(Complaint complaint, Map<String, String> params, String closureCause) {
        complaint.setStatus(CepcStatus.COMPLAINT_CLOSED);
        complaint.setClosedAt(LocalDateTime.now());
        complaint.setResolvedAt(LocalDateTime.now());
        complaint.setWorkflowStage("CLOSED");
        complaint.setClosureCause(closureCause);
        // UST576: Custom closure text
        String customClosureText = params.getOrDefault("customClosureText", "");
        if (!customClosureText.isEmpty()) {
            complaint.setCustomClosureText(customClosureText);
        }
        // UST581-584: Closure clause
        String closureClause = params.getOrDefault("closureClause", "");
        if (!closureClause.isEmpty()) {
            complaint.setClosureClause(closureClause);
        }
        // UST580: Closure authority info
        String closureAuthorityName = params.getOrDefault("closureAuthorityName", "");
        String closureAuthorityDesignation = params.getOrDefault("closureAuthorityDesignation", "");
        if (!closureAuthorityName.isEmpty()) {
            complaint.setClosureAuthorityName(closureAuthorityName);
        }
        if (!closureAuthorityDesignation.isEmpty()) {
            complaint.setClosureAuthorityDesignation(closureAuthorityDesignation);
        }
        // UST504-505, UST757, UST763: Auto-dispatch closure letter if email exists
        autoDispatchClosureLetter(complaint);
    }

    /**
     * Reads a date the client sent, tolerating an ISO timestamp.
     *
     * <p>Returns null rather than throwing: these dates arrive from an optional field on a decision form,
     * and a mistyped implementation date must not roll back a closure the officer has already authorised.
     */
    private static LocalDate parseIsoDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return LocalDate.parse(trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed);
        } catch (Exception e) {
            log.warn("Unparseable date '{}' in workflow params - ignored", raw);
            return null;
        }
    }

    /**
     * UST504-505, UST757, UST763: Auto-dispatch closure letter if complainant has email.
     * Auto-captures send date (non-editable).
     * If no email: does not auto-dispatch (manual flow required).
     */
    private void autoDispatchClosureLetter(Complaint complaint) {
        String email = complaint.getComplainantEmail();
        if (email != null && !email.isBlank()) {
            try {
                String schemeVersion = complaint.getSchemeVersion() != null
                        ? complaint.getSchemeVersion()
                        : defaultSchemeVersion;
                closureLetterService.generateClosureLetter(complaint.getComplaintNumber(), schemeVersion);
                complaint.setClosureLetterSentAt(LocalDateTime.now());
                log.info("Closure letter auto-dispatched for complaint {} to {}", complaint.getComplaintNumber(), email);
            } catch (Exception e) {
                log.error("Failed to auto-dispatch closure letter for complaint {}: {}",
                        complaint.getComplaintNumber(), e.getMessage());
            }
        } else {
            log.info("Complaint {} has no email - closure letter requires manual dispatch", complaint.getComplaintNumber());
        }
    }
}
