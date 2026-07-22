package com.rbi.cms.workflow.config;

import com.rbi.cms.workflow.handler.DraftCreationHandler;
import com.rbi.cms.workflow.handler.NotificationHandler;
import com.rbi.cms.workflow.handler.PortalRegistrationHandler;
import com.rbi.cms.workflow.service.RoundRobinAssignmentService;
import org.jbpm.runtime.manager.impl.RuntimeEnvironmentBuilder;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.manager.RuntimeEnvironment;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.manager.RuntimeManagerFactory;
import org.kie.internal.io.ResourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.persistence.EntityManagerFactory;

@Configuration
@Profile("!dev-local")
public class JbpmConfig {

    @Bean
    public RuntimeManager runtimeManager(EntityManagerFactory emf,
                                         NotificationHandler notificationHandler,
                                         PortalRegistrationHandler portalRegistrationHandler,
                                         DraftCreationHandler draftCreationHandler,
                                         RoundRobinAssignmentService assignmentService) {

        RuntimeEnvironment environment = RuntimeEnvironmentBuilder.Factory.get()
                .newDefaultBuilder()
                .entityManagerFactory(emf)
                .addAsset(
                        ResourceFactory.newClassPathResource("processes/complaint-lifecycle.bpmn2"),
                        ResourceType.BPMN2
                )
                .addAsset(
                        ResourceFactory.newClassPathResource("rules/department-routing.drl"),
                        ResourceType.DRL
                )
                .registerableItemsFactory(new CmsWorkItemHandlerFactory(
                        notificationHandler,
                        portalRegistrationHandler,
                        draftCreationHandler,
                        assignmentService
                ))
                .get();

        return RuntimeManagerFactory.Factory.get()
                .newPerProcessInstanceRuntimeManager(environment, "cms-workflow-manager");
    }
}
