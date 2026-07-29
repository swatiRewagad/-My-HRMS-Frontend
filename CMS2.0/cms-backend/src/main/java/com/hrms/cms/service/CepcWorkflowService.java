package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final Map<String, Integer> roundRobinCounters = new java.util.concurrent.ConcurrentHashMap<>();

    // ═══ Role → Allowed Actions mapping ═══
    private static final Map<String, Set<String>> ROLE_ACTIONS = Map.of(
            "CEPC_DO", Set.of(
                    "ACCEPT", "REQUEST_INFO", "INFO_RECEIVED", "FORWARD_DEPT",
                    "COMMENTS_RECEIVED", "SCHEDULE_MEETING", "SUBMIT_FOR_REVIEW",
                    "FORWARD_TO_INCHARGE", "FORWARD_TO_CONTACT"
            ),
            "CEPC_REVIEWER", Set.of(
                    "APPROVE_REVIEW", "FORWARD_TO_CLOSING_AUTHORITY", "SEND_BACK_DO"
            ),
            "CEPC_INCHARGE", Set.of(
                    "APPROVE_CLOSURE", "SEND_BACK_REVIEWER", "SEND_BACK_DO", "REASSIGN"
            ),
            "CEPC_CLOSING_AUTHORITY", Set.of(
                    "CLOSE_COMPLAINT", "SEND_BACK_INCHARGE", "FORWARD_TO_OTHER_OFFICE",
                    "FORWARD_TO_REGULATORY_BODY", "FORWARD_TO_OTHER_RBI_DEPT", "REOPEN"
            ),
            "CEPC_ADMIN", Set.of(
                    "REASSIGN", "ESCALATE", "CLOSE_COMPLAINT", "REOPEN"
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

        // Add timeline entry (user-facing)
        complaintService.addTimeline(complaint.getId(), action, actor, remarks, previousStatus, complaint.getStatus());

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
                complaint.setStatus("info_requested");
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

            case "SUBMIT_FOR_REVIEW":
                complaint.setStatus("reviewer_review");
                complaint.setAssignedRole("CEPC_REVIEWER");
                complaint.setWorkflowStage("REVIEWER_REVIEW");
                String reviewer = assignByRole("CEPC_REVIEWER");
                if (reviewer != null) complaint.setAssignedOfficer(reviewer);
                break;

            case "APPROVE_REVIEW":
                complaint.setStatus("incharge_review");
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

            case "SEND_BACK_REVIEWER":
                complaint.setStatus("reviewer_review");
                complaint.setAssignedRole("CEPC_REVIEWER");
                complaint.setWorkflowStage("REVIEWER_REVIEW");
                String backToRev = params.getOrDefault("targetUser", "");
                if (!backToRev.isEmpty()) complaint.setAssignedOfficer(backToRev);
                break;

            case "SEND_BACK_INCHARGE":
                complaint.setStatus("incharge_review");
                complaint.setAssignedRole("CEPC_INCHARGE");
                complaint.setWorkflowStage("INCHARGE_REVIEW");
                String backToIc = params.getOrDefault("targetUser", "");
                if (!backToIc.isEmpty()) complaint.setAssignedOfficer(backToIc);
                break;

            case "FORWARD_TO_INCHARGE":
                complaint.setStatus("incharge_review");
                complaint.setAssignedRole("CEPC_INCHARGE");
                complaint.setWorkflowStage("INCHARGE_REVIEW");
                String ic = assignByRole("CEPC_INCHARGE");
                if (ic != null) complaint.setAssignedOfficer(ic);
                break;

            case "FORWARD_TO_CLOSING_AUTHORITY":
                complaint.setStatus("awaiting_closure");
                complaint.setAssignedRole("CEPC_CLOSING_AUTHORITY");
                complaint.setWorkflowStage("AWAITING_CLOSURE");
                String ca = assignByRole("CEPC_CLOSING_AUTHORITY");
                if (ca != null) complaint.setAssignedOfficer(ca);
                break;

            case "APPROVE_CLOSURE":
                complaint.setStatus("awaiting_closure");
                complaint.setAssignedRole("CEPC_CLOSING_AUTHORITY");
                complaint.setWorkflowStage("AWAITING_CLOSURE");
                break;

            case "CLOSE_COMPLAINT":
                complaint.setStatus("closed");
                complaint.setClosedAt(LocalDateTime.now());
                complaint.setResolvedAt(LocalDateTime.now());
                complaint.setWorkflowStage("CLOSED");
                String closureCause = params.getOrDefault("closureCause", "RESOLVED");
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
                break;

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

            case "FORWARD_TO_OTHER_OFFICE":
                complaint.setStatus("forwarded_external");
                complaint.setWorkflowStage("FORWARDED_OTHER_OFFICE");
                String otherOffice = params.getOrDefault("targetOffice", "");
                if (!otherOffice.isEmpty()) {
                    complaint.setAssignedOfficer(otherOffice);
                }
                break;

            case "FORWARD_TO_REGULATORY_BODY":
                complaint.setStatus("forwarded_external");
                complaint.setWorkflowStage("FORWARDED_REGULATORY_BODY");
                String regulatoryBody = params.getOrDefault("targetBody", "");
                if (!regulatoryBody.isEmpty()) {
                    complaint.setAssignedOfficer(regulatoryBody);
                }
                break;

            case "FORWARD_TO_OTHER_RBI_DEPT":
                complaint.setStatus("forwarded_external");
                complaint.setWorkflowStage("FORWARDED_OTHER_RBI_DEPT");
                String rbiDept = params.getOrDefault("targetDept", "");
                if (!rbiDept.isEmpty()) {
                    complaint.setAssignedOfficer(rbiDept);
                }
                break;

            case "REOPEN":
                complaint.setStatus("in_progress");
                complaint.setResolvedAt(null);
                complaint.setClosedAt(null);
                complaint.setWorkflowStage("REOPENED");
                // Track reopen count
                int currentReopenCount = complaint.getReopenCount() != null ? complaint.getReopenCount() : 0;
                complaint.setReopenCount(currentReopenCount + 1);
                complaint.setLastReopenedAt(LocalDateTime.now());
                // Recalculate SLA from reopen time
                cepcSlaService.applySlaDeadline(complaint);
                break;

            default:
                throw new IllegalArgumentException("Unknown CEPC action: " + action);
        }
    }

    /**
     * Determine if an action is valid given the current complaint state.
     */
    private boolean isActionValidForState(Complaint complaint, String action) {
        String status = complaint.getStatus();
        String stage = complaint.getWorkflowStage();

        if (status == null) return false;

        switch (action) {
            case "ACCEPT":
                return "assigned".equals(status) || "sent_back".equals(status);

            case "REQUEST_INFO":
            case "FORWARD_DEPT":
            case "SCHEDULE_MEETING":
            case "SUBMIT_FOR_REVIEW":
            case "FORWARD_TO_INCHARGE":
            case "FORWARD_TO_CONTACT":
                return "in_progress".equals(status) || "assigned".equals(status);

            case "INFO_RECEIVED":
                return "info_requested".equals(status);

            case "COMMENTS_RECEIVED":
                return "forwarded".equals(status);

            case "APPROVE_REVIEW":
            case "SEND_BACK_DO":
                return "reviewer_review".equals(status);

            case "FORWARD_TO_CLOSING_AUTHORITY":
                return "reviewer_review".equals(status) || "incharge_review".equals(status);

            case "APPROVE_CLOSURE":
            case "SEND_BACK_REVIEWER":
                return "incharge_review".equals(status);

            case "CLOSE_COMPLAINT":
            case "SEND_BACK_INCHARGE":
            case "FORWARD_TO_OTHER_OFFICE":
            case "FORWARD_TO_REGULATORY_BODY":
            case "FORWARD_TO_OTHER_RBI_DEPT":
                return "awaiting_closure".equals(status);

            case "REOPEN":
                return "closed".equals(status) || "resolved".equals(status);

            case "REASSIGN":
                // Can reassign at any active state
                return !List.of("closed", "resolved", "rejected", "withdrawn").contains(status);

            case "ESCALATE":
                return "in_progress".equals(status) || "assigned".equals(status);

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
     * UST504-505, UST757, UST763: Auto-dispatch closure letter if complainant has email.
     * Auto-captures send date (non-editable).
     * If no email: does not auto-dispatch (manual flow required).
     */
    private void autoDispatchClosureLetter(Complaint complaint) {
        String email = complaint.getComplainantEmail();
        if (email != null && !email.isBlank()) {
            try {
                String schemeVersion = complaint.getSchemeVersion() != null ? complaint.getSchemeVersion() : "RBIOS_2026";
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
