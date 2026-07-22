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
public class DraftCreationHandler implements WorkItemHandler {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        String complaintId = (String) workItem.getParameter("complaintId");
        String channel = (String) workItem.getParameter("channel");

        log.info("[DRAFT] Creating draft for CRPC complaint {} via channel: {}", complaintId, channel);

        try {
            String payload = String.format(
                    "{\"complaintId\":\"%s\",\"channel\":\"%s\",\"status\":\"DRAFT_CREATED\"}",
                    complaintId, channel != null ? channel : "EMAIL"
            );
            kafkaTemplate.send("complaint.ingested", complaintId, payload);
        } catch (Exception e) {
            log.error("[DRAFT] Failed to publish draft event for {}: {}", complaintId, e.getMessage());
        }

        Map<String, Object> results = new HashMap<>();
        results.put("draftCreated", true);
        manager.completeWorkItem(workItem.getId(), results);
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        log.warn("[DRAFT] Draft creation aborted for: {}", workItem.getParameter("complaintId"));
        manager.abortWorkItem(workItem.getId());
    }
}
