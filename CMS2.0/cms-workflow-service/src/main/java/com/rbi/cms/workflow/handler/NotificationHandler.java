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
public class NotificationHandler implements WorkItemHandler {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        String complaintId = (String) workItem.getParameter("complaintId");
        String resolutionSummary = (String) workItem.getParameter("resolutionSummary");

        log.info("[NOTIFY] Sending resolution notification for complaint: {}", complaintId);

        try {
            String notificationPayload = String.format(
                    "{\"complaintId\":\"%s\",\"type\":\"RESOLUTION\",\"summary\":\"%s\"}",
                    complaintId, resolutionSummary != null ? resolutionSummary : "Your complaint has been resolved."
            );
            kafkaTemplate.send("complaint.notification", complaintId, notificationPayload);
        } catch (Exception e) {
            log.error("[NOTIFY] Failed to send notification for {}: {}", complaintId, e.getMessage());
        }

        Map<String, Object> results = new HashMap<>();
        results.put("notificationSent", true);
        manager.completeWorkItem(workItem.getId(), results);
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        log.warn("[NOTIFY] Notification work item aborted for complaint: {}", workItem.getParameter("complaintId"));
        manager.abortWorkItem(workItem.getId());
    }
}
