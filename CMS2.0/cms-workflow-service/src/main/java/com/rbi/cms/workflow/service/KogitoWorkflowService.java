package com.rbi.cms.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.event.ComplaintEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.kogito.Model;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.WorkItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kogito-based workflow service.
 * Uses the auto-generated process from complaint-lifecycle.bpmn2.
 * Kogito compiles BPMN into a Process<T> bean at build time.
 */
@Slf4j
@Service
@Profile("!dev-local")
@RequiredArgsConstructor
public class KogitoWorkflowService implements ComplaintWorkflowProcessor {

    @Qualifier("complaint_lifecycle")
    private final Process<? extends Model> complaintProcess;

    private final RoundRobinAssignmentService assignmentService;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, String> complaintProcessMap = new ConcurrentHashMap<>();

    @Override
    public String startComplaintWorkflow(ComplaintEvent event) {
        log.info("[KOGITO] Starting workflow for complaint: {}", event.getComplaintId());

        Model model = complaintProcess.createModel();
        Map<String, Object> params = new HashMap<>();
        params.put("complaintId", event.getComplaintId());
        params.put("correlationId", event.getCorrelationId());

        String channel = "PORTAL";
        String category = "GENERAL";
        String priority = "MEDIUM";
        String entityName = "";
        String entityType = "";
        String entitySize = "";

        try {
            if (event.getPayload() != null && event.getPayload().startsWith("{")) {
                Map<String, String> payload = objectMapper.readValue(event.getPayload(), Map.class);
                channel = payload.getOrDefault("channel", payload.getOrDefault("filingType", "PORTAL"));
                category = payload.getOrDefault("category", "GENERAL");
                priority = payload.getOrDefault("priority", "MEDIUM");
                entityName = payload.getOrDefault("entityName", payload.getOrDefault("entityCode", ""));
                entityType = payload.getOrDefault("entityType", "");
                entitySize = payload.getOrDefault("entitySize", "");
            }
        } catch (Exception e) {
            log.warn("[KOGITO] Failed to parse event payload: {}", e.getMessage());
        }

        params.put("channel", channel);
        params.put("category", category);
        params.put("priority", priority);
        params.put("entityName", entityName);
        params.put("entityType", entityType);
        params.put("entitySize", entitySize);

        // Pre-route department (inline routing until Drools businessRuleTask integration is complete)
        String department = "RBIO";
        if ("LARGE".equals(entitySize) || "PRIVATE_SECTOR".equals(entityType)) {
            department = "CEPC";
        }
        params.put("department", department);

        // Pre-assign officers (inline assignment until Drools ruleflow-groups are wired)
        String assignedOfficer = safeAssign(department + "_OFFICER", "OFFICER_001");
        params.put("assignedOfficer", assignedOfficer);
        params.put("assignedDeo", safeAssign("CRPC_DEO", "DEO_001"));
        params.put("assignedReviewer", safeAssign("CRPC_REVIEWER", "REVIEWER_001"));
        params.put("assignedSupervisor", "SUPERVISOR_001");
        params.put("assignedConciliator", "CONCILIATOR_001");
        params.put("assignedAdjudicator", "ADJUDICATOR_001");

        log.info("[KOGITO] Routed to department={}, officer={} (entity={}, size={}, type={})",
                department, assignedOfficer, entityName, entitySize, entityType);

        model.fromMap(params);

        ProcessInstance<?> instance = complaintProcess.createInstance(model);
        instance.start();

        String processInstanceId = instance.id();
        complaintProcessMap.put(event.getComplaintId(), processInstanceId);

        log.info("[KOGITO] Workflow started: complaint={}, processId={}, status={}",
                event.getComplaintId(), processInstanceId, instance.status());

        return processInstanceId;
    }

    @Override
    public void transitionState(String complaintId, ComplaintStatus targetStatus, String remarks) {
        log.info("[KOGITO] Transition: complaint={} → {}", complaintId, targetStatus);

        String processInstanceId = complaintProcessMap.get(complaintId);
        if (processInstanceId == null) {
            log.warn("[KOGITO] No active process for complaint: {}", complaintId);
            return;
        }

        Optional<? extends ProcessInstance<?>> instanceOpt = complaintProcess.instances().findById(processInstanceId);
        if (instanceOpt.isEmpty()) {
            log.warn("[KOGITO] Process instance not found: {}", processInstanceId);
            return;
        }

        ProcessInstance<?> instance = instanceOpt.get();
        List<WorkItem> workItems = instance.workItems();

        for (WorkItem workItem : workItems) {
            Map<String, Object> results = new HashMap<>();
            results.put("targetStatus", targetStatus.name());
            results.put("remarks", remarks);
            results.put("complaintId", complaintId);

            populateDecisionVariable(results, workItem.getName(), targetStatus);

            instance.completeWorkItem(workItem.getId(), results);
            log.info("[KOGITO] Completed task '{}' for complaint: {}", workItem.getName(), complaintId);
            break;
        }
    }

    @Override
    public void escalateComplaint(String complaintId, String reason) {
        log.info("[KOGITO] Escalating complaint: {} - reason: {}", complaintId, reason);

        String processInstanceId = complaintProcessMap.get(complaintId);
        if (processInstanceId == null) {
            log.warn("[KOGITO] No active process for complaint: {}", complaintId);
            return;
        }

        Optional<? extends ProcessInstance<?>> instanceOpt = complaintProcess.instances().findById(processInstanceId);
        if (instanceOpt.isEmpty()) return;

        ProcessInstance<?> instance = instanceOpt.get();
        instance.send(org.kie.kogito.process.SignalFactory.of("escalation", Map.of(
                "complaintId", complaintId,
                "escalationReason", reason
        )));

        log.info("[KOGITO] Escalation signal sent for complaint: {}", complaintId);
    }

    public void completeHumanTask(String complaintId, String userId, Map<String, Object> taskData) {
        String processInstanceId = complaintProcessMap.get(complaintId);
        if (processInstanceId == null) {
            log.warn("[KOGITO] No active process for complaint: {}", complaintId);
            return;
        }

        Optional<? extends ProcessInstance<?>> instanceOpt = complaintProcess.instances().findById(processInstanceId);
        if (instanceOpt.isEmpty()) return;

        ProcessInstance<?> instance = instanceOpt.get();
        List<WorkItem> workItems = instance.workItems();

        for (WorkItem workItem : workItems) {
            instance.completeWorkItem(workItem.getId(), taskData);
            log.info("[KOGITO] Human task '{}' completed by {} for complaint {}",
                    workItem.getName(), userId, complaintId);
            break;
        }
    }

    public List<WorkItem> getActiveWorkItems(String complaintId) {
        String processInstanceId = complaintProcessMap.get(complaintId);
        if (processInstanceId == null) return Collections.emptyList();

        Optional<? extends ProcessInstance<?>> instanceOpt = complaintProcess.instances().findById(processInstanceId);
        return instanceOpt.map(ProcessInstance::workItems).orElse(Collections.emptyList());
    }

    public Map<String, Object> getProcessInstanceInfo(String complaintId) {
        Map<String, Object> info = new HashMap<>();
        String processInstanceId = complaintProcessMap.get(complaintId);
        info.put("processInstanceId", processInstanceId);
        info.put("tracked", processInstanceId != null);

        if (processInstanceId == null) return info;

        Optional<? extends ProcessInstance<?>> instanceOpt = complaintProcess.instances().findById(processInstanceId);
        if (instanceOpt.isEmpty()) {
            info.put("found", false);
            info.put("note", "Instance not found - may have completed or errored");
        } else {
            ProcessInstance<?> instance = instanceOpt.get();
            info.put("found", true);
            info.put("status", instance.status());
            info.put("statusName", switch (instance.status()) {
                case ProcessInstance.STATE_ACTIVE -> "ACTIVE";
                case ProcessInstance.STATE_COMPLETED -> "COMPLETED";
                case ProcessInstance.STATE_ABORTED -> "ABORTED";
                case ProcessInstance.STATE_ERROR -> "ERROR";
                default -> "UNKNOWN(" + instance.status() + ")";
            });
            Model variables = (Model) instance.variables();
            info.put("variables", variables.toMap());
            if (instance.status() == ProcessInstance.STATE_ERROR) {
                info.put("error", instance.error().isPresent() ?
                        instance.error().get().errorMessage() : "Unknown error");
                info.put("errorNodeId", instance.error().isPresent() ?
                        instance.error().get().failedNodeId() : "unknown");
            }
        }
        return info;
    }

    private String safeAssign(String roleGroup, String fallback) {
        try {
            String result = assignmentService.assignNext(roleGroup);
            return result != null ? result : fallback;
        } catch (Exception e) {
            log.warn("[KOGITO] Assignment failed for {}: {}. Using fallback: {}", roleGroup, e.getMessage(), fallback);
            return fallback;
        }
    }

    private void populateDecisionVariable(Map<String, Object> results, String taskName, ComplaintStatus status) {
        if (taskName == null) return;

        if (taskName.contains("DEO")) {
            results.put("deoDecision", status == ComplaintStatus.REJECTED ? "NON_MAINTAINABLE" : "MAINTAINABLE");
        } else if (taskName.contains("Reviewer")) {
            switch (status) {
                case APPROVED -> results.put("reviewerDecision", "APPROVE");
                case REJECTED -> results.put("reviewerDecision", "NOT_A_COMPLAINT");
                case SENT_BACK -> results.put("reviewerDecision", "SENT_BACK_TO_DEO");
                default -> results.put("reviewerDecision", "APPROVE");
            }
        } else if (taskName.contains("Officer")) {
            switch (status) {
                case RESOLVED -> results.put("officerDecision", "RESOLVED");
                case ESCALATED -> results.put("officerDecision", "ESCALATE_TO_SUPERVISOR");
                default -> results.put("officerDecision", "RESOLVED");
            }
        } else if (taskName.contains("Supervisor")) {
            switch (status) {
                case RESOLVED -> results.put("supervisorDecision", "RESOLVED");
                case SENT_BACK -> results.put("supervisorDecision", "RETURN_TO_OFFICER");
                default -> results.put("supervisorDecision", "RESOLVED");
            }
        } else if (taskName.contains("Conciliation")) {
            results.put("conciliationOutcome", status == ComplaintStatus.RESOLVED ? "SETTLED" : "FAILED_ADJUDICATE");
        } else if (taskName.contains("Adjudication")) {
            results.put("adjudicationOutcome", status == ComplaintStatus.RESOLVED ? "AWARDED" : "REJECTED");
        }
    }
}
