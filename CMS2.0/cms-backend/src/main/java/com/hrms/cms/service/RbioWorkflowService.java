package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private final Map<String, Integer> roundRobinCounters = new ConcurrentHashMap<>();

    // ═══ Role -> Allowed Actions mapping ═══
    private static final Map<String, Set<String>> ROLE_ACTIONS = Map.of(
            "RBIO_OFFICER", Set.of(
                    "ACCEPT", "TAKE_ACTION", "RESOLVE", "REJECT", "ESCALATE",
                    "REQUEST_INFO", "SCHEDULE_MEETING", "FORWARD_TO_CONCILIATION", "ISSUE_ADVISORY"
            ),
            "RBIO_SUPERVISOR", Set.of(
                    "APPROVE", "RETURN_TO_OFFICER", "RESOLVE", "ESCALATE",
                    "FORWARD_TO_ADJUDICATION", "FORWARD_TO_CONCILIATION", "REASSIGN", "ISSUE_ADVISORY"
            ),
            "RBIO_CONCILIATOR", Set.of(
                    "CONCILIATION_SUCCESS", "CONCILIATION_FAILED", "SCHEDULE_MEETING", "ESCALATE_TO_ADJUDICATION"
            ),
            "RBIO_ADJUDICATOR", Set.of(
                    "ADJUDICATION_AWARD", "ADJUDICATION_REJECT", "ISSUE_NOTICE_13_1", "IMPLEAD_PARTY"
            ),
            "RBIO_ADMIN", Set.of(
                    "REASSIGN", "ESCALATE", "CLOSE_COMPLAINT", "REOPEN", "BULK_ASSIGN"
            )
    );

    // All recognized RBIO actions
    private static final Set<String> ALL_RBIO_ACTIONS;

    static {
        Set<String> all = new HashSet<>();
        ROLE_ACTIONS.values().forEach(all::addAll);
        ALL_RBIO_ACTIONS = Collections.unmodifiableSet(all);
    }

    /**
     * Check if an action is an RBIO-specific action.
     */
    public boolean isRbioAction(String action) {
        return action != null && ALL_RBIO_ACTIONS.contains(action.toUpperCase());
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

        // Validate role authorization
        if (!userRole.isBlank() && !validateRoleAuthorization(userRole, action)) {
            throw new IllegalArgumentException(
                    String.format("Role '%s' is not authorized to perform action '%s'", userRole, action));
        }

        // Execute the state transition
        executeAction(complaint, action.toUpperCase(), params);

        // Save the complaint
        complaintRepository.save(complaint);

        // Add timeline entry
        complaintService.addTimeline(complaint.getId(), action, actor, remarks, previousStatus, complaint.getStatus());

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
     * Validate whether a given role is authorized to perform the specified action.
     *
     * @param userRole the user's RBIO role
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
     * @param userRole        the user's RBIO role
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
            case "TAKE_ACTION":
                complaint.setStatus("in_progress");
                complaint.setAssignedOfficer(params.getOrDefault("actor", complaint.getAssignedOfficer()));
                complaint.setWorkflowStage("EXAMINATION");
                rbioSlaService.applyStageSla(complaint, "OFFICER_ASSESSMENT");
                break;

            case "APPROVE":
                complaint.setStatus("approved");
                complaint.setAssignedRole(getNextRole(complaint.getAssignedRole()));
                break;

            case "REJECT":
                complaint.setStatus("rejected");
                complaint.setResolvedAt(LocalDateTime.now());
                complaint.setClosureCause("REJECTED");
                complaint.setWorkflowStage("CLOSED");
                break;

            case "ESCALATE":
                complaint.setStatus("escalated");
                complaint.setEscalatedAt(LocalDateTime.now());
                complaint.setAssignedRole(getNextRole(complaint.getAssignedRole()));
                complaint.setWorkflowStage("ESCALATED");
                break;

            case "RETURN_TO_OFFICER":
                complaint.setStatus("returned");
                complaint.setAssignedRole("RBIO_OFFICER");
                complaint.setWorkflowStage("RETURNED_TO_OFFICER");
                String targetOfficer = params.getOrDefault("targetUser", "");
                if (!targetOfficer.isEmpty()) {
                    complaint.setAssignedOfficer(targetOfficer);
                }
                break;

            case "RESOLVE":
                complaint.setStatus("resolved");
                complaint.setResolvedAt(LocalDateTime.now());
                complaint.setWorkflowStage("RESOLVED");
                complaint.setClosureCause(params.getOrDefault("closureCause", "RESOLVED"));
                break;

            case "REQUEST_INFO":
                complaint.setStatus("info_requested");
                complaint.setWorkflowStage("AWAITING_INFO");
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

            case "ISSUE_ADVISORY":
                complaint.setStatus("advisory_issued");
                complaint.setWorkflowStage("ADVISORY_ISSUED");
                complaint.setClosureCause("ADVISORY");
                String advisoryText = params.getOrDefault("advisoryText", params.getOrDefault("remarks", ""));
                complaint.setAdvisoryText(advisoryText);
                complaint.setAdvisoryIssuedAt(LocalDateTime.now());
                break;

            case "FORWARD_TO_CONCILIATION":
                complaint.setStatus("conciliation");
                complaint.setAssignedRole("RBIO_CONCILIATOR");
                complaint.setWorkflowStage("CONCILIATION");
                String conciliator = assignByRole("RBIO_CONCILIATOR");
                if (conciliator != null) {
                    complaint.setAssignedOfficer(conciliator);
                }
                rbioSlaService.applyStageSla(complaint, "CONCILIATION");
                break;

            case "FORWARD_TO_ADJUDICATION":
                complaint.setStatus("adjudication");
                complaint.setAssignedRole("RBIO_ADJUDICATOR");
                complaint.setWorkflowStage("ADJUDICATION");
                String adjudicator = assignByRole("RBIO_ADJUDICATOR");
                if (adjudicator != null) {
                    complaint.setAssignedOfficer(adjudicator);
                }
                rbioSlaService.applyStageSla(complaint, "ADJUDICATION");
                break;

            case "ESCALATE_TO_ADJUDICATION":
                complaint.setStatus("adjudication");
                complaint.setAssignedRole("RBIO_ADJUDICATOR");
                complaint.setWorkflowStage("ADJUDICATION");
                complaint.setConciliationOutcome("FAILED");
                String adjudicatorEsc = assignByRole("RBIO_ADJUDICATOR");
                if (adjudicatorEsc != null) {
                    complaint.setAssignedOfficer(adjudicatorEsc);
                }
                rbioSlaService.applyStageSla(complaint, "ADJUDICATION");
                break;

            case "CONCILIATION_SUCCESS":
                complaint.setStatus("conciliated");
                complaint.setResolvedAt(LocalDateTime.now());
                complaint.setConciliationOutcome("SUCCESS");
                complaint.setConciliationDate(LocalDateTime.now());
                complaint.setWorkflowStage("CONCILIATION_COMPLETE");
                complaint.setClosureCause("CONCILIATION_SUCCESS");
                break;

            case "CONCILIATION_FAILED":
                complaint.setStatus("escalated");
                complaint.setAssignedRole("RBIO_ADJUDICATOR");
                complaint.setConciliationOutcome("FAILED");
                complaint.setConciliationDate(LocalDateTime.now());
                complaint.setWorkflowStage("CONCILIATION_FAILED");
                String adjAfterFail = assignByRole("RBIO_ADJUDICATOR");
                if (adjAfterFail != null) {
                    complaint.setAssignedOfficer(adjAfterFail);
                }
                rbioSlaService.applyStageSla(complaint, "ADJUDICATION");
                break;

            case "ADJUDICATION_AWARD":
                // Validate compensation cap BEFORE allowing the award
                String amountStr = params.getOrDefault("awardAmount", "0");
                String compensationType = params.getOrDefault("compensationType",
                        complaint.getCompensationType() != null ? complaint.getCompensationType() : "COMBINED");
                BigDecimal awardAmount;
                try {
                    awardAmount = new BigDecimal(amountStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid award amount: " + amountStr);
                }
                // This will throw IllegalArgumentException if cap exceeded (BLOCKING)
                rbioCompensationService.validateAward(awardAmount, compensationType);

                complaint.setStatus("adjudicated");
                complaint.setResolvedAt(LocalDateTime.now());
                complaint.setAwardAmount(awardAmount);
                complaint.setCompensationType(compensationType);
                complaint.setAdjudicationDate(LocalDateTime.now());
                complaint.setAdjudicationOutcome("AWARD_ISSUED");
                complaint.setWorkflowStage("AWARD_ISSUED");
                complaint.setClosureCause("ADJUDICATION_AWARD");
                break;

            case "ADJUDICATION_REJECT":
                complaint.setStatus("rejected");
                complaint.setResolvedAt(LocalDateTime.now());
                complaint.setAdjudicationDate(LocalDateTime.now());
                complaint.setAdjudicationOutcome("REJECTED");
                complaint.setWorkflowStage("ADJUDICATION_REJECTED");
                complaint.setClosureCause("ADJUDICATION_REJECTED");
                break;

            case "ISSUE_NOTICE_13_1":
                complaint.setWorkflowStage("NOTICE_13_1_ISSUED");
                complaint.setNotice131IssuedAt(LocalDateTime.now());
                // Timeline entry serves as the notice record
                break;

            case "IMPLEAD_PARTY":
                String partyName = params.getOrDefault("partyName", "");
                if (partyName.isBlank()) {
                    throw new IllegalArgumentException("partyName is required for IMPLEAD_PARTY action");
                }
                String existing = complaint.getImpleadedParties();
                if (existing == null || existing.isBlank()) {
                    complaint.setImpleadedParties(partyName);
                } else {
                    complaint.setImpleadedParties(existing + "," + partyName);
                }
                complaint.setWorkflowStage("PARTY_IMPLEADED");
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

            case "CLOSE_COMPLAINT":
                complaint.setStatus("closed");
                complaint.setClosedAt(LocalDateTime.now());
                complaint.setResolvedAt(LocalDateTime.now());
                complaint.setWorkflowStage("CLOSED");
                complaint.setClosureCause(params.getOrDefault("closureCause", "ADMIN_CLOSED"));
                break;

            case "REOPEN":
                complaint.setStatus("in_progress");
                complaint.setResolvedAt(null);
                complaint.setClosedAt(null);
                complaint.setWorkflowStage("REOPENED");
                int currentReopenCount = complaint.getReopenCount() != null ? complaint.getReopenCount() : 0;
                complaint.setReopenCount(currentReopenCount + 1);
                complaint.setLastReopenedAt(LocalDateTime.now());
                // Re-apply SLA from reopen time
                rbioSlaService.applyStageSla(complaint, "OFFICER_ASSESSMENT");
                break;

            case "BULK_ASSIGN":
                // Bulk assign is handled at the controller level; individual action simply reassigns
                String bulkTarget = params.getOrDefault("targetUser", "");
                String bulkRole = params.getOrDefault("targetRole", "RBIO_OFFICER");
                if (!bulkTarget.isEmpty()) {
                    complaint.setAssignedOfficer(bulkTarget);
                }
                complaint.setAssignedRole(bulkRole);
                complaint.setStatus("assigned");
                complaint.setWorkflowStage("BULK_ASSIGNED");
                break;

            default:
                throw new IllegalArgumentException("Unknown RBIO action: " + action);
        }
    }

    /**
     * Determine if an action is valid given the current complaint state.
     */
    private boolean isActionValidForState(Complaint complaint, String action) {
        String status = complaint.getStatus();
        if (status == null) return false;

        switch (action) {
            case "ACCEPT":
            case "TAKE_ACTION":
                return "assigned".equals(status) || "returned".equals(status);

            case "APPROVE":
                return "in_progress".equals(status);

            case "REJECT":
                return "in_progress".equals(status) || "assigned".equals(status);

            case "ESCALATE":
                return "in_progress".equals(status) || "assigned".equals(status);

            case "RETURN_TO_OFFICER":
                return "escalated".equals(status) || "in_progress".equals(status);

            case "RESOLVE":
                return "in_progress".equals(status);

            case "REQUEST_INFO":
            case "SCHEDULE_MEETING":
                return "in_progress".equals(status) || "assigned".equals(status) || "conciliation".equals(status);

            case "ISSUE_ADVISORY":
                return "in_progress".equals(status) || "assigned".equals(status);

            case "FORWARD_TO_CONCILIATION":
                return "in_progress".equals(status) || "escalated".equals(status);

            case "FORWARD_TO_ADJUDICATION":
                return "in_progress".equals(status) || "escalated".equals(status);

            case "ESCALATE_TO_ADJUDICATION":
                return "conciliation".equals(status) || "escalated".equals(status);

            case "CONCILIATION_SUCCESS":
            case "CONCILIATION_FAILED":
                return "conciliation".equals(status);

            case "ADJUDICATION_AWARD":
            case "ADJUDICATION_REJECT":
            case "ISSUE_NOTICE_13_1":
            case "IMPLEAD_PARTY":
                return "adjudication".equals(status);

            case "REASSIGN":
            case "BULK_ASSIGN":
                return !List.of("closed", "resolved", "rejected", "withdrawn", "adjudicated", "conciliated").contains(status);

            case "CLOSE_COMPLAINT":
                return !List.of("closed").contains(status);

            case "REOPEN":
                return "closed".equals(status) || "resolved".equals(status) ||
                        "adjudicated".equals(status) || "conciliated".equals(status);

            default:
                return true;
        }
    }

    private String getNextRole(String currentRole) {
        if (currentRole == null) return "RBIO_OFFICER";
        Map<String, String> escalation = Map.of(
                "RBIO_OFFICER", "RBIO_SUPERVISOR",
                "RBIO_SUPERVISOR", "RBIO_CONCILIATOR",
                "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR"
        );
        return escalation.getOrDefault(currentRole, currentRole);
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
}
