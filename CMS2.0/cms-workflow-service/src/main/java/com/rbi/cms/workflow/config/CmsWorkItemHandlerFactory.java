package com.rbi.cms.workflow.config;

import com.rbi.cms.workflow.handler.DraftCreationHandler;
import com.rbi.cms.workflow.handler.NotificationHandler;
import com.rbi.cms.workflow.handler.PortalRegistrationHandler;
import com.rbi.cms.workflow.service.RoundRobinAssignmentService;
import lombok.RequiredArgsConstructor;
import org.kie.api.runtime.manager.RegisterableItemsFactory;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.task.TaskLifeCycleEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CmsWorkItemHandlerFactory implements RegisterableItemsFactory {

    private final NotificationHandler notificationHandler;
    private final PortalRegistrationHandler portalRegistrationHandler;
    private final DraftCreationHandler draftCreationHandler;
    private final RoundRobinAssignmentService assignmentService;

    @Override
    public Map<String, WorkItemHandler> getWorkItemHandlers(RuntimeEngine runtime) {
        Map<String, WorkItemHandler> handlers = new HashMap<>();
        handlers.put("com.rbi.cms.workflow.handler.NotificationHandler", notificationHandler);
        handlers.put("com.rbi.cms.workflow.handler.PortalRegistrationHandler", portalRegistrationHandler);
        handlers.put("com.rbi.cms.workflow.handler.DraftCreationHandler", draftCreationHandler);
        return handlers;
    }

    @Override
    public List<TaskLifeCycleEventListener> getTaskListeners() {
        return List.of();
    }

    @Override
    public Map<String, Object> getGlobals(RuntimeEngine runtime) {
        Map<String, Object> globals = new HashMap<>();
        globals.put("assignmentService", assignmentService);
        return globals;
    }
}
