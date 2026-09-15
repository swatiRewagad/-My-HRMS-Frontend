package com.rbi.cms.workflow.config;

import com.rbi.cms.workflow.handler.DraftCreationHandler;
import com.rbi.cms.workflow.handler.HumanTaskHandler;
import com.rbi.cms.workflow.handler.NotificationHandler;
import com.rbi.cms.workflow.handler.PassThroughHandler;
import com.rbi.cms.workflow.handler.PortalRegistrationHandler;
import lombok.extern.slf4j.Slf4j;
import org.jbpm.bpmn2.xml.BPMNDISemanticModule;
import org.jbpm.bpmn2.xml.BPMNExtensionsSemanticModule;
import org.jbpm.bpmn2.xml.BPMNSemanticModule;
import org.jbpm.compiler.xml.XmlProcessReader;
import org.jbpm.compiler.xml.core.SemanticModules;
import org.kie.api.definition.process.Process;
import org.kie.kogito.Addons;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.StaticApplication;
import org.kie.kogito.StaticConfig;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.bpmn2.BpmnProcess;
import org.kie.kogito.process.bpmn2.BpmnProcesses;
import org.kie.kogito.jobs.JobDescription;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.process.impl.CachedWorkItemHandlerConfig;
import org.kie.kogito.process.impl.StaticProcessConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Configuration
@Profile("!dev-local")
public class KogitoConfig {

    @Bean
    public Application kogitoApplication(
            PortalRegistrationHandler portalHandler,
            DraftCreationHandler draftHandler,
            NotificationHandler notificationHandler,
            PassThroughHandler passThroughHandler,
            HumanTaskHandler humanTaskHandler) {

        JobsService noOpJobsService = new JobsService() {
            @Override
            public String scheduleJob(JobDescription job) {
                log.debug("[KOGITO] Timer scheduled (no-op): {}", job.id());
                return job.id();
            }
            @Override
            public boolean cancelJob(String id) { return true; }
            @Override
            public String rescheduleJob(JobDescription job) { return job.id(); }
        };

        StaticProcessConfig processConfig = new StaticProcessConfig(noOpJobsService);
        CachedWorkItemHandlerConfig handlerConfig = (CachedWorkItemHandlerConfig) processConfig.workItemHandlers();
        handlerConfig.register(portalHandler.getName(), portalHandler);
        handlerConfig.register(draftHandler.getName(), draftHandler);
        handlerConfig.register(notificationHandler.getName(), notificationHandler);
        handlerConfig.register(passThroughHandler.getName(), passThroughHandler);
        handlerConfig.register(humanTaskHandler.getName(), humanTaskHandler);
        log.info("[KOGITO-CONFIG] Registered work item handlers: {}", handlerConfig.names());

        StaticConfig config = new StaticConfig(Addons.EMTPY, processConfig);
        return new StaticApplication(config);
    }

    @Bean("complaint_lifecycle")
    public org.kie.kogito.process.Process<? extends Model> complaintLifecycleProcess(Application application) throws Exception {
        Process kieProcess = loadBpmnProcess("processes/complaint-lifecycle.bpmn2");
        ProcessConfig processConfig = application.config().get(ProcessConfig.class);
        BpmnProcess bpmnProcess = new BpmnProcess(kieProcess, processConfig, application);
        bpmnProcess.activate();
        log.info("[KOGITO-CONFIG] Loaded process: id={}, name={}", kieProcess.getId(), kieProcess.getName());
        return bpmnProcess;
    }

    @Bean
    public Processes processes(org.kie.kogito.process.Process<? extends Model> complaintProcess) {
        BpmnProcesses processes = new BpmnProcesses();
        processes.addProcess(complaintProcess);
        log.info("[KOGITO-CONFIG] Registered {} process(es)", processes.processIds().size());
        return processes;
    }

    private Process loadBpmnProcess(String resourcePath) throws Exception {
        SemanticModules modules = new SemanticModules();
        modules.addSemanticModule(new BPMNSemanticModule());
        modules.addSemanticModule(new BPMNDISemanticModule());
        modules.addSemanticModule(new BPMNExtensionsSemanticModule());

        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            XmlProcessReader reader = new XmlProcessReader(modules, getClass().getClassLoader());
            List<Process> processes = reader.read(is);
            if (processes.isEmpty()) {
                throw new IllegalStateException("No processes found in " + resourcePath);
            }
            return processes.get(0);
        }
    }
}
