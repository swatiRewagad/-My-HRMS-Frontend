package com.rbi.cms.workflow.handler;

import lombok.extern.slf4j.Slf4j;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component("Human Task")
public class HumanTaskHandler extends DefaultKogitoWorkItemHandler {

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager,
            KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
        log.info("[HUMAN-TASK] Task activated: name={}, params={}",
                workItem.getNodeInstance().getNodeName(), workItem.getParameters().keySet());
        return Optional.empty();
    }

    @Override
    public String getName() {
        return "Human Task";
    }
}
