package com.rbi.cms.workflow.service;

import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.event.ComplaintEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@Profile("!dev-local")
@RequiredArgsConstructor
public class WorkflowService implements ComplaintWorkflowProcessor {

    private final RuntimeManager runtimeManager;

    private static final String PROCESS_ID = "com.rbi.cms.complaint-lifecycle";

    public String startComplaintWorkflow(ComplaintEvent event) {
        log.info("Starting workflow for complaint: {}", event.getComplaintId());

        RuntimeEngine runtimeEngine = runtimeManager.getRuntimeEngine(null);
        KieSession kieSession = runtimeEngine.getKieSession();

        Map<String, Object> params = new HashMap<>();
        params.put("complaintId", event.getComplaintId());
        params.put("category", event.getPayload());
        params.put("correlationId", event.getCorrelationId());

        ProcessInstance processInstance = kieSession.startProcess(PROCESS_ID, params);

        log.info("Workflow started for complaint: {}, processInstanceId: {}",
                event.getComplaintId(), processInstance.getId());

        return processInstance.getId();
    }

    public void transitionState(String complaintId, ComplaintStatus targetStatus, String remarks) {
        log.info("Transitioning complaint {} to status: {}", complaintId, targetStatus);

        RuntimeEngine runtimeEngine = runtimeManager.getRuntimeEngine(null);
        KieSession kieSession = runtimeEngine.getKieSession();

        Map<String, Object> params = new HashMap<>();
        params.put("complaintId", complaintId);
        params.put("targetStatus", targetStatus.name());
        params.put("remarks", remarks);

        kieSession.signalEvent("transition", params);
    }

    public void escalateComplaint(String complaintId, String reason) {
        log.info("Escalating complaint: {} - reason: {}", complaintId, reason);

        RuntimeEngine runtimeEngine = runtimeManager.getRuntimeEngine(null);
        KieSession kieSession = runtimeEngine.getKieSession();

        Map<String, Object> params = new HashMap<>();
        params.put("complaintId", complaintId);
        params.put("escalationReason", reason);

        kieSession.signalEvent("escalation", params);
    }
}
