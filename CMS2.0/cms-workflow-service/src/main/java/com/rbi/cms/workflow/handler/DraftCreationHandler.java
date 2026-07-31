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
@Component("com.rbi.cms.workflow.handler.DraftCreationHandler")
public class DraftCreationHandler extends DefaultKogitoWorkItemHandler {

    private final WorkflowEventPublisher eventPublisher;

    public DraftCreationHandler(WorkflowEventPublisher eventPublisher) {
        super();
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager,
            KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {

        String complaintId = (String) workItem.getParameter("complaintId");
        String channel = (String) workItem.getParameter("channel");

        log.info("[DRAFT] Creating draft for CRPC complaint {} via channel: {}", complaintId, channel);

        try {
            eventPublisher.publishInProgress(complaintId, "CRPC_DEO");
        } catch (Exception e) {
            log.error("[DRAFT] Failed to publish draft event for {}: {}", complaintId, e.getMessage());
        }

        manager.completeWorkItem(workItem.getStringId(), Map.of("draftCreated", true));
        return Optional.empty();
    }

    @Override
    public String getName() {
        return "com.rbi.cms.workflow.handler.DraftCreationHandler";
    }
}
