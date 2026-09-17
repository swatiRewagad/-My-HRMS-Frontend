package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the RE Activity Status sweeps (UST849, UST850).
 *
 * Cron expressions come from properties rather than being compiled in, following
 * cms-sla-monitor-service's SlaCheckScheduler rather than NotificationScheduledTasks, which
 * hardcodes eight of them. The overdue sweep runs more often than the nudge sweep because a missed
 * deadline is time-critical for the officer, whereas a nudge is a daily-digest concern.
 *
 * Failures are caught per sweep so one bad row cannot silence the scheduler for the other sweep or
 * for subsequent ticks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReActivityScheduler {

    private final ReActivitySweepService sweepService;

    @Scheduled(cron = "${cms.re.activity.overdue-cron:0 */15 * * * *}")
    public void runOverdueSweep() {
        try {
            sweepService.sweepOverdue();
        } catch (Exception e) {
            log.error("RE activity overdue sweep failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "${cms.re.activity.nudge-cron:0 0 9 * * *}")
    public void runNudgeSweep() {
        try {
            sweepService.sweepNudges();
        } catch (Exception e) {
            log.error("RE activity nudge sweep failed: {}", e.getMessage(), e);
        }
    }
}
