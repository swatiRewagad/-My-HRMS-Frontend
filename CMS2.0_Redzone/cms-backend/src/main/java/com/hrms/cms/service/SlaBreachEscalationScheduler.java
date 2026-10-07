package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the SLA breach sweep ({@link SlaBreachEscalationService}).
 *
 * <p>The cron comes from a property rather than being compiled in, following
 * {@link ReActivityScheduler} rather than {@code NotificationScheduledTasks}, which hardcodes eight
 * of them. That matters more than usual here: this sweep is the first thing that has ever acted on
 * {@code sla_deadline}, so the operator needs to be able to retime it — or stop it — without a
 * redeploy. Setting {@code cms.sla.breach-cron} to a quiet schedule retimes it; setting
 * {@code cms.sla.breach_escalation_enabled=false} in SYSTEM_CONFIG stops it taking effect at the next
 * tick with no restart at all.
 *
 * <p>Fifteen minutes matches the RE overdue sweep. A missed SLA deadline is time-critical for the
 * officer, and the marker means a tick that finds nothing costs one indexed query.
 *
 * <p>Failures are caught here so one bad row cannot silence the scheduler for subsequent ticks — and
 * because this method shares an unconfigured pool with 48 other {@code @Scheduled} methods and the
 * WebSocket broker, an escaping exception is not a local problem.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlaBreachEscalationScheduler {

    private final SlaBreachEscalationService sweepService;

    @Scheduled(cron = "${cms.sla.breach-cron:0 */15 * * * *}")
    public void runBreachSweep() {
        try {
            sweepService.sweepBreaches();
        } catch (Exception e) {
            log.error("SLA breach sweep failed: {}", e.getMessage(), e);
        }
    }
}
