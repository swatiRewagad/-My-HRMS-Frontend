package com.rbi.cms.sla.scheduler;

import com.rbi.cms.sla.service.SlaMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaCheckScheduler {

    private final SlaMonitorService slaMonitorService;

    @Scheduled(cron = "${cms.sla.check-cron:0 */15 * * * *}")
    public void checkSlaBreaches() {
        log.info("Starting SLA breach check...");
        int breaches = slaMonitorService.detectAndEscalateBreaches();
        log.info("SLA breach check completed. Breaches detected: {}", breaches);
    }

    @Scheduled(cron = "${cms.sla.warning-cron:0 */30 * * * *}")
    public void checkSlaWarnings() {
        log.info("Starting SLA warning check...");
        int warnings = slaMonitorService.detectWarnings();
        log.info("SLA warning check completed. Warnings: {}", warnings);
    }
}
