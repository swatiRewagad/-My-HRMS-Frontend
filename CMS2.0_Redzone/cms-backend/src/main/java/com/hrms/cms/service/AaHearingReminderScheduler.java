package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron trigger for the AA hearing reminder sweep.
 *
 * <p>Separated from the sweep so the sweep stays directly callable from a test without waiting on a
 * clock, following ReActivityScheduler and AaEscalationScheduler. The cron is property-driven and the
 * work itself is config-gated inside the sweep.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AaHearingReminderScheduler {

    private final AaHearingReminderSweepService sweepService;

    @Scheduled(cron = "${cms.aa.hearing.reminder-cron:0 0 8 * * *}")
    public void runHearingReminderSweep() {
        try {
            sweepService.sweepUpcomingHearings();
        } catch (Exception e) {
            log.error("AA hearing reminder sweep failed: {}", e.getMessage(), e);
        }
    }
}
