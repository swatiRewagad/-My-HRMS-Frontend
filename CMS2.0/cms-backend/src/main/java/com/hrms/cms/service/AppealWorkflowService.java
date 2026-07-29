package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealTimeline;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.AppealTimelineRepository;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final Map<String, Integer> roundRobinCounters = new java.util.concurrent.ConcurrentHashMap<>();

    private static final List<String> CLOSED_STATUSES = List.of("closed", "rejected", "order_passed");

    // ═══════════════════════════════════════════════════════════
    // Role -> Allowed actions mapping
    // ═══════════════════════════════════════════════════════════

    private static final Map<String, List<String>> ROLE_ACTIONS = Map.of(
            "AA_REGISTRAR", List.of("ACCEPT", "REJECT", "ASSIGN_TO_BENCH", "REQUEST_DOCUMENTS"),
            "AA_BENCH_OFFICER", List.of("SCHEDULE_HEARING", "PREPARE_BRIEF", "FORWARD_TO_AUTHORITY", "SEND_BACK_REGISTRAR"),
            "AA_AUTHORITY", List.of("PASS_ORDER", "SCHEDULE_HEARING", "REMAND_TO_OMBUDSMAN", "DISMISS"),
            "AA_ADMIN", List.of("REASSIGN", "CLOSE", "REOPEN")
    );

    // ═══════════════════════════════════════════════════════════
    // File Appeal
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public Map<String, Object> fileAppeal(Map<String, String> request) {
        String originalComplaintNumber = request.get("originalComplaintNumber");
        String classificationType = request.get("classificationType");
        String appealGround = request.get("appealGround");
        String reliefSought = request.get("reliefSought");
        String appellantName = request.get("appellantName");
        String appellantEmail = request.get("appellantEmail");
        String appellantPhone = request.get("appellantPhone");

        // Validate required fields
        if (originalComplaintNumber == null || originalComplaintNumber.isBlank()) {
            throw new IllegalArgumentException("originalComplaintNumber is required");
        }
        if (classificationType == null || classificationType.isBlank()) {
            throw new IllegalArgumentException("classificationType is required");
        }
        if (!"APPEAL".equals(classificationType) && !"REPRESENTATION".equals(classificationType)) {
            throw new IllegalArgumentException("classificationType must be APPEAL or REPRESENTATION");
        }
        if (appellantName == null || appellantName.isBlank()) {
            throw new IllegalArgumentException("appellantName is required");
        }
        if (appealGround == null || appealGround.isBlank()) {
            throw new IllegalArgumentException("appealGround is required");
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
                .appellantName(appellantName)
                .appellantEmail(appellantEmail)
                .appellantPhone(appellantPhone)
                .status("filed")
                .assignedRole("AA_REGISTRAR")
                .assignedOfficer(assignByRole("AA_REGISTRAR"))
                .priority("high")
                .workflowStage("FILED")
                .build();

        Appeal saved = appealRepository.save(appeal);

        // Add timeline entry
        addTimeline(appealNumber, "FILED", "SYSTEM", null,
                "Appeal filed against complaint " + originalComplaintNumber, null, "filed");

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

        String actor = params.getOrDefault("actor", "");
        String actorRole = params.getOrDefault("actorRole", "");
        String remarks = params.getOrDefault("remarks", "");
        String prevStatus = appeal.getStatus();

        // Validate action is allowed for the role
        if (!actorRole.isBlank()) {
            List<String> allowedActions = ROLE_ACTIONS.getOrDefault(actorRole, Collections.emptyList());
            if (!allowedActions.contains(action.toUpperCase())) {
                throw new IllegalArgumentException("Action " + action + " is not permitted for role " + actorRole);
            }
        }

        // Validate classificationType immutability - reject any attempt to change it
        if (params.containsKey("classificationType")) {
            String requestedType = params.get("classificationType");
            if (!appeal.getClassificationType().equals(requestedType)) {
                throw new IllegalArgumentException("classificationType is immutable and cannot be changed after creation");
            }
        }

        switch (action.toUpperCase()) {
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
                appeal.setAssignedRole("AA_BENCH_OFFICER");
                appeal.setAssignedOfficer(assignByRole("AA_BENCH_OFFICER"));
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
                appeal.setHearingDate(LocalDateTime.parse(hearingDateStr));
                appeal.setHearingVenue(hearingVenue);
                appeal.setStatus("hearing_scheduled");
                appeal.setWorkflowStage("HEARING_SCHEDULED");
                break;

            case "PREPARE_BRIEF":
                appeal.setWorkflowStage("BRIEF_PREPARED");
                break;

            case "FORWARD_TO_AUTHORITY":
                appeal.setAssignedRole("AA_AUTHORITY");
                appeal.setAssignedOfficer(assignByRole("AA_AUTHORITY"));
                appeal.setWorkflowStage("FORWARDED_TO_AUTHORITY");
                break;

            case "SEND_BACK_REGISTRAR":
                appeal.setAssignedRole("AA_REGISTRAR");
                appeal.setAssignedOfficer(assignByRole("AA_REGISTRAR"));
                appeal.setWorkflowStage("SENT_BACK_TO_REGISTRAR");
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
                reopenOriginalComplaint(appeal.getOriginalComplaintNumber(), actor);
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
                appeal.setAssignedRole(newRole);
                appeal.setAssignedOfficer(newOfficer.isBlank() ? assignByRole(newRole) : newOfficer);
                appeal.setWorkflowStage("REASSIGNED");
                break;

            case "CLOSE":
                appeal.setStatus("closed");
                appeal.setClosedAt(LocalDateTime.now());
                appeal.setClosureCause(params.getOrDefault("closureCause", "ADMIN_CLOSED"));
                appeal.setWorkflowStage("CLOSED");
                break;

            case "REOPEN":
                appeal.setStatus("under_review");
                appeal.setClosedAt(null);
                appeal.setClosureCause(null);
                appeal.setWorkflowStage("REOPENED");
                break;

            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }

        appealRepository.save(appeal);

        // Add timeline entry
        addTimeline(appealNumber, action.toUpperCase(), actor, actorRole, remarks, prevStatus, appeal.getStatus());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appealNumber", appeal.getAppealNumber());
        result.put("action", action.toUpperCase());
        result.put("newStatus", appeal.getStatus());
        result.put("assignedRole", appeal.getAssignedRole());
        result.put("assignedOfficer", appeal.getAssignedOfficer());
        result.put("workflowStage", appeal.getWorkflowStage());

        return result;
    }

    // ═══════════════════════════════════════════════════════════
    // Available Actions
    // ═══════════════════════════════════════════════════════════

    public List<String> getAvailableActions(String appealNumber, String userRole) {
        Optional<Appeal> opt = appealRepository.findByAppealNumber(appealNumber);
        if (opt.isEmpty()) return Collections.emptyList();

        Appeal appeal = opt.get();

        // If appeal is in a terminal state, no actions available (except for ADMIN)
        if (CLOSED_STATUSES.contains(appeal.getStatus()) && !"AA_ADMIN".equals(userRole)) {
            return Collections.emptyList();
        }

        return ROLE_ACTIONS.getOrDefault(userRole, Collections.emptyList());
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

    // ═══════════════════════════════════════════════════════════
    // Private helpers
    // ═══════════════════════════════════════════════════════════

    private void reopenOriginalComplaint(String complaintNumber, String actor) {
        complaintRepository.findByComplaintNumber(complaintNumber).ifPresent(complaint -> {
            complaint.setStatus("in_progress");
            complaint.setClosedAt(null);
            complaint.setReopenCount(complaint.getReopenCount() != null ? complaint.getReopenCount() + 1 : 1);
            complaint.setLastReopenedAt(LocalDateTime.now());
            complaintRepository.save(complaint);
            log.info("Reopened original complaint {} due to REMAND_TO_OMBUDSMAN by {}", complaintNumber, actor);
        });
    }

    private String generateAppealNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "APL-" + date + "-" + uuid;
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
            log.debug("Failed to assign by role {}: {}", role, e.getMessage());
            return null;
        }
    }
}
