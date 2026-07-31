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
@Component("com.rbi.cms.workflow.handler.NotificationHandler")
public class NotificationHandler extends DefaultKogitoWorkItemHandler {

    private final WorkflowEventPublisher eventPublisher;

    public NotificationHandler(WorkflowEventPublisher eventPublisher) {
        super();
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager,
            KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {

        String complaintId = (String) workItem.getParameter("complaintId");
        String resolutionSummary = (String) workItem.getParameter("resolutionSummary");

        log.info("[NOTIFY] Sending resolution notification for complaint: {}", complaintId);

        try {
            eventPublisher.publishResolved(complaintId, resolutionSummary);
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
