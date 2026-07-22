package com.rbi.cms.workflow.service;

import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.event.ComplaintEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Profile("!dev-local")
@RequiredArgsConstructor
public class WorkflowService implements ComplaintWorkflowProcessor {

    private final RuntimeManager runtimeManager;
    private final ObjectMapper objectMapper;

    private static final String PROCESS_ID = "com.rbi.cms.complaint-lifecycle";
    private final ConcurrentHashMap<String, Long> complaintProcessMap = new ConcurrentHashMap<>();

    @Override
    public String startComplaintWorkflow(ComplaintEvent event) {
        log.info("Starting jBPM workflow for complaint: {}", event.getComplaintId());

        RuntimeEngine runtimeEngine = runtimeManager.getRuntimeEngine(null);
        KieSession kieSession = runtimeEngine.getKieSession();

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
            log.warn("Failed to parse event payload: {}", e.getMessage());
        }

        params.put("channel", channel);
        params.put("category", category);
        params.put("priority", priority);
        params.put("entityName", entityName);
        params.put("entityType", entityType);
        params.put("entitySize", entitySize);

        ProcessInstance processInstance = kieSession.startProcess(PROCESS_ID, params);
        long processInstanceId = processInstance.getId();

        complaintProcessMap.put(event.getComplaintId(), processInstanceId);

        log.info("jBPM workflow started: complaint={}, processInstanceId={}, channel={}",
                event.getComplaintId(), processInstanceId, channel);

        return String.valueOf(processInstanceId);
    }

    @Override
    public void transitionState(String complaintId, ComplaintStatus targetStatus, String remarks) {
        log.info("Transitioning complaint {} to status: {}", complaintId, targetStatus);

        Long processInstanceId = complaintProcessMap.get(complaintId);
        if (processInstanceId == null) {
            log.warn("No active process instance found for complaint: {}", complaintId);
            return;
        }

        RuntimeEngine runtimeEngine = runtimeManager.getRuntimeEngine(
                ProcessInstanceIdContext.get(processInstanceId));
        KieSession kieSession = runtimeEngine.getKieSession();
        TaskService taskService = runtimeEngine.getTaskService();

        List<TaskSummary> tasks = taskService.getTasksByProcessInstanceId(processInstanceId);
        for (TaskSummary task : tasks) {
            if ("Reserved".equals(task.getStatusId()) || "InProgress".equals(task.getStatusId())) {
                Map<String, Object> results = new HashMap<>();
                results.put("targetStatus", targetStatus.name());
                results.put("remarks", remarks);
                results.put("complaintId", complaintId);

                populateDecisionVariable(results, task.getName(), targetStatus);

                taskService.start(task.getId(), task.getActualOwnerId());
                taskService.complete(task.getId(), task.getActualOwnerId(), results);

                log.info("Completed jBPM task: {} for complaint: {}", task.getName(), complaintId);
                break;
            }
        }

        runtimeManager.disposeRuntimeEngine(runtimeEngine);
    }

    @Override
    public void escalateComplaint(String complaintId, String reason) {
        log.info("Escalating complaint: {} - reason: {}", complaintId, reason);

        Long processInstanceId = complaintProcessMap.get(complaintId);
        if (processInstanceId == null) {
            log.warn("No active process instance found for complaint: {}", complaintId);
            return;
        }

        RuntimeEngine runtimeEngine = runtimeManager.getRuntimeEngine(
                ProcessInstanceIdContext.get(processInstanceId));
        KieSession kieSession = runtimeEngine.getKieSession();

        kieSession.signalEvent("escalation", Map.of(
                "complaintId", complaintId,
                "escalationReason", reason
        ), processInstanceId);

        log.info("Escalation signal sent for complaint: {}, processInstance: {}", complaintId, processInstanceId);
        runtimeManager.disposeRuntimeEngine(runtimeEngine);
    }

    public void completeHumanTask(String complaintId, String userId, Map<String, Object> taskData) {
        Long processInstanceId = complaintProcessMap.get(complaintId);
        if (processInstanceId == null) {
            log.warn("No active process instance for complaint: {}", complaintId);
            return;
        }

        RuntimeEngine runtimeEngine = runtimeManager.getRuntimeEngine(
                ProcessInstanceIdContext.get(processInstanceId));
        TaskService taskService = runtimeEngine.getTaskService();

        List<TaskSummary> tasks = taskService.getTasksByProcessInstanceId(processInstanceId);
        for (TaskSummary task : tasks) {
            if ("Ready".equals(task.getStatusId()) || "Reserved".equals(task.getStatusId())) {
                taskService.claim(task.getId(), userId);
                taskService.start(task.getId(), userId);
                taskService.complete(task.getId(), userId, taskData);
                log.info("Human task {} completed by {} for complaint {}", task.getName(), userId, complaintId);
                break;
            }
        }

        runtimeManager.disposeRuntimeEngine(runtimeEngine);
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
