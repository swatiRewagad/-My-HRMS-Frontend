package com.rbi.cms.rules.engine;

import com.rbi.cms.rules.entity.RuleDefinition;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class DynamicRuleEngine {

    private final AtomicReference<KieContainer> containerRef = new AtomicReference<>();

    public synchronized void reloadRules(List<RuleDefinition> activeRules) {
        log.info("Reloading {} active rules into KieContainer", activeRules.size());

        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();

        for (RuleDefinition rule : activeRules) {
            String path = "src/main/resources/rules/" + rule.getRuleCode() + ".drl";
            kfs.write(path, kieServices.getResources()
                    .newByteArrayResource(rule.getDrlContent().getBytes())
                    .setResourceType(ResourceType.DRL));
            log.debug("Added rule to KieFileSystem: {} (v{})", rule.getRuleCode(), rule.getVersion());
        }

        KieBuilder builder = kieServices.newKieBuilder(kfs).buildAll();
        Results results = builder.getResults();

        if (results.hasMessages(Message.Level.ERROR)) {
            String errors = results.getMessages().toString();
            log.error("Rule compilation failed: {}", errors);
            throw new RuleCompilationException("Rule compilation errors: " + errors);
        }

        if (results.hasMessages(Message.Level.WARNING)) {
            log.warn("Rule compilation warnings: {}", results.getMessages());
        }

        KieContainer newContainer = kieServices.newKieContainer(
                builder.getKieModule().getReleaseId());

        KieContainer oldContainer = containerRef.getAndSet(newContainer);
        if (oldContainer != null) {
            oldContainer.dispose();
        }

        log.info("KieContainer reloaded successfully with {} rules", activeRules.size());
    }

    public KieSession newKieSession() {
        KieContainer container = containerRef.get();
        if (container == null) {
            throw new IllegalStateException("Rule engine not initialized. Deploy rules first.");
        }
        return container.newKieSession();
    }

    public boolean isInitialized() {
        return containerRef.get() != null;
    }

    public RuleValidationResult validateDrl(String drlContent) {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.write("src/main/resources/rules/validation-test.drl",
                kieServices.getResources()
                        .newByteArrayResource(drlContent.getBytes())
                        .setResourceType(ResourceType.DRL));

        KieBuilder builder = kieServices.newKieBuilder(kfs).buildAll();
        Results results = builder.getResults();

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        results.getMessages(Message.Level.ERROR)
                .forEach(m -> errors.add(m.getText()));
        results.getMessages(Message.Level.WARNING)
                .forEach(m -> warnings.add(m.getText()));

        return new RuleValidationResult(!results.hasMessages(Message.Level.ERROR), errors, warnings);
    }

    public record RuleValidationResult(boolean valid, List<String> errors, List<String> warnings) {}
}
