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
@Component("com.rbi.cms.workflow.handler.PortalRegistrationHandler")
public class PortalRegistrationHandler extends DefaultKogitoWorkItemHandler {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PortalRegistrationHandler(KafkaTemplate<String, String> kafkaTemplate) {
        super();
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager,
            KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {

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

        manager.completeWorkItem(workItem.getStringId(), Map.of("registered", true));
        return Optional.empty();
    }

    @Override
    public String getName() {
        return "com.rbi.cms.workflow.handler.PortalRegistrationHandler";
    }
}
