package com.rbi.cms.rules.config;

import com.rbi.cms.rules.service.RuleManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngineInitializer {

    private final RuleManagementService ruleManagementService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Auto-deploying active rules on startup...");
        var result = ruleManagementService.deployRules("system-startup");
        log.info("Startup deployment: {} rules deployed (status: {})", result.getRulesDeployed(), result.getStatus());
    }
}
