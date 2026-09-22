package com.rbi.cms.workflow.handler;

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
@Component("com.rbi.cms.workflow.handler.PassThroughHandler")
public class PassThroughHandler extends DefaultKogitoWorkItemHandler {

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager,
            KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
        log.debug("[PASS-THROUGH] Auto-completing task: {}", workItem.getNodeInstance().getNodeName());
        manager.completeWorkItem(workItem.getStringId(), Map.of());
        return Optional.empty();
    }

    @Override
    public String getName() {
        return "com.rbi.cms.workflow.handler.PassThroughHandler";
    }
}
