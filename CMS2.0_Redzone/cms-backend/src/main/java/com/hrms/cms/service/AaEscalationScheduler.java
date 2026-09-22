package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ticks the unclaimed-draft escalation sweep.
 *
 * Cron comes from a property rather than being compiled in, following ReActivityScheduler. The sweep
 * itself is additionally gated on a SYSTEM_CONFIG flag, so an operator can stop it without a redeploy
 * and so a @SpringBootTest booting the full context does not fire real alerts.
 *
 * Failures are caught here so one bad tick cannot kill the schedule for every subsequent one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AaEscalationScheduler {

    private final AaEscalationSweepService sweepService;

    @Scheduled(cron = "${cms.aa.escalation.cron:0 */30 * * * *}")
    public void runUnclaimedSweep() {
        try {
            sweepService.sweepUnclaimed();
        } catch (Exception e) {
            log.error("AA unclaimed-draft escalation sweep failed: {}", e.getMessage(), e);
        }
    }
}
