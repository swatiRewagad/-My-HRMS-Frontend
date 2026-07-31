package com.rbi.cms.workflow.handler;

import com.rbi.cms.workflow.service.WorkflowEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component("com.rbi.cms.workflow.handler.PortalRegistrationHandler")
public class PortalRegistrationHandler extends DefaultKogitoWorkItemHandler {

    private final WorkflowEventPublisher eventPublisher;

    public PortalRegistrationHandler(WorkflowEventPublisher eventPublisher) {
        super();
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager,
            KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {

        String complaintId = (String) workItem.getParameter("complaintId");
        String channel = (String) workItem.getParameter("channel");
        String department = (String) workItem.getParameter("department");
        String assignedOfficer = (String) workItem.getParameter("assignedOfficer");

        log.info("[PORTAL-REG] Registering complaint {} from channel={}, dept={}", complaintId, channel, department);

        try {
            eventPublisher.publishAssigned(
                    complaintId,
                    department != null ? department : "RBIO",
                    assignedOfficer != null ? assignedOfficer : "UNASSIGNED"
            );
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
