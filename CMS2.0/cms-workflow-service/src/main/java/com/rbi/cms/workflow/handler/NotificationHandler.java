package com.rbi.cms.workflow.handler;

import lombok.extern.slf4j.Slf4j;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component("com.rbi.cms.workflow.handler.NotificationHandler")
public class NotificationHandler extends DefaultKogitoWorkItemHandler {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public NotificationHandler(KafkaTemplate<String, String> kafkaTemplate) {
        super();
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager,
            KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {

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

        manager.completeWorkItem(workItem.getStringId(), Map.of("notificationSent", true));
        return Optional.empty();
    }

    @Override
    public String getName() {
        return "com.rbi.cms.workflow.handler.NotificationHandler";
    }
}
