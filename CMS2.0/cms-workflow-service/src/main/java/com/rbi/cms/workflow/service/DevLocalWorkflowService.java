package com.rbi.cms.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.event.ComplaintEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev-local simulation of the jBPM complaint-lifecycle.bpmn2 process.
 * Mirrors the BPMN flow: ChannelGateway → (Portal direct / CRPC pipeline) → DepartmentRouting → RBIO or CEPC.
 */
@Slf4j
@Service
@Profile("dev-local")
@Primary
@RequiredArgsConstructor
public class DevLocalWorkflowService implements ComplaintWorkflowProcessor {

    private static final Set<String> CEPC_ENTITIES = Set.of(
            "HDFC", "ICICI", "AXIS", "KOTAK", "INDUSIND", "YES", "IDFC", "BANDHAN",
            "RBL", "FEDERAL", "SOUTH_INDIAN", "KARUR_VYSYA", "CITY_UNION", "DCB",
            "DHANLAXMI", "LAKSHMI_VILAS", "NAINITAL", "TAMILNAD_MERCANTILE"
    );

    private static final Set<String> RBIO_ENTITIES = Set.of(
            "SBI", "PNB", "BOB", "CANARA", "UNION", "IOB", "CENTRAL_BANK",
            "UCO", "BANK_OF_INDIA", "INDIAN_BANK", "PUNJAB_SIND", "BANK_OF_MAHARASHTRA",
            "IDBI"
    );

    private final ConcurrentHashMap<String, String> activeProcesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ComplaintStatus> complaintStatuses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkflowState> workflowStates = new ConcurrentHashMap<>();
    private final DevLocalTaskQueryService taskQueryService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public String startComplaintWorkflow(ComplaintEvent event) {
        String processId = UUID.randomUUID().toString();
        activeProcesses.put(event.getComplaintId(), processId);

        String category = "GENERAL";
        String priority = "MEDIUM";
        String channel = "WEB_PORTAL";
        String entityCode = "";

        try {
            if (event.getPayload() != null && event.getPayload().startsWith("{")) {
                Map<String, String> payload = objectMapper.readValue(event.getPayload(), Map.class);
                category = payload.getOrDefault("category", "GENERAL");
                priority = payload.getOrDefault("priority", "MEDIUM");
                channel = payload.getOrDefault("channel", payload.getOrDefault("filingType", "WEB_PORTAL"));
                entityCode = payload.getOrDefault("entityCode", "");
            }
        } catch (Exception e) {
            log.warn("Failed to parse payload: {}", e.getMessage());
        }

        // ═══════════════════════════════════════════════════════════
        // BPMN ChannelGateway: route based on channel/filingType
        // ═══════════════════════════════════════════════════════════
        String department;
        String role;
        String stage;

        if ("EMAIL".equals(channel) || "PHYSICAL_LETTER".equals(channel)) {
            // PATH B: CRPC Pipeline (Email/Physical Letter)
            // BPMN: DraftCreation → DEO Assignment → DEO Assessment → Reviewer → forward to RBIO/CEPC
            department = "CRPC";
            role = "DEO";
            stage = "DATA_ENTRY";
            complaintStatuses.put(event.getComplaintId(), ComplaintStatus.ASSIGNED);

            String targetDept = resolveDepartment(entityCode);
            log.info("[jBPM WORKFLOW] Channel={} → CRPC Pipeline (DEO → Reviewer → {})", channel, targetDept);

            workflowStates.put(event.getComplaintId(), WorkflowState.builder()
                    .processId(processId)
                    .channel(channel)
                    .stage(stage)
                    .department(department)
                    .targetDepartment(targetDept)
                    .entityCode(entityCode)
                    .assignedRole(role)
                    .build());

        } else {
            // PATH A: Public Portal (WEB_PORTAL) → always goes to RBIO
            department = "RBIO";
            role = "RBIO_OFFICER";
            stage = "INITIAL_REVIEW";
            complaintStatuses.put(event.getComplaintId(), ComplaintStatus.ASSIGNED);

            log.info("[jBPM WORKFLOW] Channel=WEB_PORTAL → Direct route to RBIO");

            workflowStates.put(event.getComplaintId(), WorkflowState.builder()
                    .processId(processId)
                    .channel("PORTAL")
                    .stage(stage)
                    .department(department)
                    .targetDepartment(department)
                    .entityCode(entityCode)
                    .assignedRole(role)
                    .build());
        }

        taskQueryService.registerTask(event.getComplaintId(), category, priority, department + "_TEAM");

        routeToBackend(event.getComplaintId(), department, role, stage);

        log.info("[jBPM WORKFLOW] Process {} started: complaint={} channel={} dept={} role={} stage={}",
                processId, event.getComplaintId(), channel, department, role, stage);
        return processId;
    }

    public void transitionState(String complaintId, ComplaintStatus targetStatus, String remarks) {
        complaintStatuses.put(complaintId, targetStatus);
        taskQueryService.updateTaskStatus(complaintId, targetStatus.name());

        WorkflowState state = workflowStates.get(complaintId);
        if (state != null) {
            handleBpmnTransition(complaintId, state, targetStatus, remarks);
        }

        updateBackendStatus(complaintId, targetStatus.name(), remarks, "WORKFLOW_ENGINE");

        log.info("[jBPM WORKFLOW] Transition: complaint={} → status={} (remarks: {})",
                complaintId, targetStatus, remarks);
    }

    public void escalateComplaint(String complaintId, String reason) {
        complaintStatuses.put(complaintId, ComplaintStatus.ESCALATED);
        taskQueryService.updateTaskStatus(complaintId, "ESCALATED");

        WorkflowState state = workflowStates.get(complaintId);
        if (state != null) {
            // BPMN: SLA breach → escalate to Supervisor
            String newRole = state.getDepartment() + "_SUPERVISOR";
            state.setStage("SUPERVISOR_REVIEW");
            state.setAssignedRole(newRole);
            log.info("[jBPM WORKFLOW] SLA escalation: {} → {} ({})", complaintId, newRole, reason);
            routeToBackend(complaintId, state.getDepartment(), newRole, "SUPERVISOR_REVIEW");
        }

        updateBackendStatus(complaintId, "ESCALATED", reason, "WORKFLOW_ENGINE");
        log.info("[jBPM WORKFLOW] Escalated: {} - reason: {}", complaintId, reason);
    }

    /**
     * BPMN Inter-department transfer: CEPC ↔ RBIO
     * Allows an officer to transfer a complaint to the other department if the entity belongs there.
     */
    public void transferDepartment(String complaintId, String fromDepartment, String toDepartment, String reason) {
        WorkflowState state = workflowStates.get(complaintId);
        if (state == null) {
            log.warn("[jBPM WORKFLOW] No active process for complaint: {}", complaintId);
            return;
        }

        String newRole = toDepartment + "_OFFICER";
        state.setDepartment(toDepartment);
        state.setTargetDepartment(toDepartment);
        state.setAssignedRole(newRole);
        state.setStage("TRANSFERRED");

        routeToBackend(complaintId, toDepartment, newRole, "TRANSFERRED");
        log.info("[jBPM WORKFLOW] Transfer: {} from {} → {} (reason: {})", complaintId, fromDepartment, toDepartment, reason);
    }

    /**
     * BPMN CRPC forward: after DEO + Reviewer approval, forward to target department (RBIO/CEPC).
     */
    public void forwardFromCrpc(String complaintId) {
        WorkflowState state = workflowStates.get(complaintId);
        if (state == null || !"CRPC".equals(state.getDepartment())) {
            log.warn("[jBPM WORKFLOW] Cannot forward - not in CRPC pipeline: {}", complaintId);
            return;
        }

        String targetDept = state.getTargetDepartment();
        String targetRole = targetDept + "_OFFICER";
        state.setDepartment(targetDept);
        state.setAssignedRole(targetRole);
        state.setStage("APPROVAL_REVIEW");

        routeToBackend(complaintId, targetDept, targetRole, "APPROVAL_REVIEW");
        log.info("[jBPM WORKFLOW] CRPC → {} forwarded: complaint={}", targetDept, complaintId);
    }

    /**
     * Simulate BPMN Drools department-routing businessRuleTask.
     * Resolves entity code to RBIO or CEPC based on entity mapping.
     */
    private String resolveDepartment(String entityCode) {
        if (entityCode == null || entityCode.isBlank()) return "RBIO";
        String code = entityCode.toUpperCase();
        if (CEPC_ENTITIES.contains(code)) return "CEPC";
        if (RBIO_ENTITIES.contains(code)) return "RBIO";
        return "RBIO";
    }

    private void handleBpmnTransition(String complaintId, WorkflowState state,
                                       ComplaintStatus targetStatus, String remarks) {
        switch (targetStatus) {
            case RESOLVED -> {
                state.setStage("RESOLVED");
                log.info("[jBPM WORKFLOW] Resolution path: {} → NotifyCustomer → ClosureTimer", complaintId);
            }
            case IN_PROGRESS -> {
                if ("DATA_ENTRY".equals(state.getStage())) {
                    // DEO completed assessment, move to Reviewer
                    state.setStage("REVIEWER_ASSESSMENT");
                    state.setAssignedRole("REVIEWER");
                    routeToBackend(complaintId, state.getDepartment(), "REVIEWER", "REVIEWER_ASSESSMENT");
                }
            }
            case CLOSED -> state.setStage("CLOSED");
            default -> {}
        }
    }

    private void routeToBackend(String complaintId, String department, String role, String stage) {
        try {
            String routeUrl = String.format("http://localhost:8082/api/v1/workflow/route/%s", complaintId);
            Map<String, String> body = Map.of(
                    "department", department,
                    "role", role,
                    "officer", role.toLowerCase().replace("_", "."),
                    "stage", stage
            );
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, new org.springframework.http.HttpHeaders() {{
                setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            }});
            restTemplate.exchange(routeUrl, HttpMethod.POST, request, Void.class);
        } catch (Exception e) {
            log.warn("[jBPM WORKFLOW] Could not route complaint in backend: {}", e.getMessage());
        }
    }

    private void updateBackendStatus(String complaintId, String status, String remarks, String performedBy) {
        try {
            String url = String.format(
                    "http://localhost:8082/cms-ingestion/api/v1/complaints/%s/status?status=%s&remarks=%s&performedBy=%s",
                    complaintId, status,
                    remarks != null ? java.net.URLEncoder.encode(remarks, "UTF-8") : "",
                    performedBy);
            restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, Void.class);
        } catch (Exception e) {
            log.warn("[jBPM WORKFLOW] Could not update backend status: {}", e.getMessage());
        }
    }

    @lombok.Builder
    @lombok.Data
    private static class WorkflowState {
        private String processId;
        private String channel;
        private String stage;
        private String department;
        private String targetDepartment;
        private String entityCode;
        private String assignedRole;
    }
}
