package com.rbi.cms.workflow.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortalRegistrationHandler implements WorkItemHandler {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        String complaintId = (String) workItem.getParameter("complaintId");
        String channel = (String) workItem.getParameter("channel");

        log.info("[PORTAL-REG] Registering complaint {} from channel: {}", complaintId, channel);

        try {
            String payload = String.format(
                    "{\"complaintId\":\"%s\",\"channel\":\"PORTAL\",\"status\":\"REGISTERED\"}",
                    complaintId
            );
            kafkaTemplate.send("complaint.assigned", complaintId, payload);
        } catch (Exception e) {
            log.error("[PORTAL-REG] Failed to publish registration event for {}: {}", complaintId, e.getMessage());
        }

        Map<String, Object> results = new HashMap<>();
        results.put("registered", true);
        manager.completeWorkItem(workItem.getId(), results);
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        log.warn("[PORTAL-REG] Registration aborted for: {}", workItem.getParameter("complaintId"));
        manager.abortWorkItem(workItem.getId());
    }
}
