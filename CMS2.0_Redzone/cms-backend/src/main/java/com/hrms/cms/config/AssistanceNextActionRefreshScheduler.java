package com.hrms.cms.config;

import com.hrms.cms.service.AssistanceNextActionRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the next-action rollup refresh on a fixed interval (Brief 21 §6.2).
 *
 * <h2>A plain {@code @Scheduled}, unlike {@link ReDeadlineSweepScheduler}</h2>
 * That class implements {@code SchedulingConfigurer} with a custom {@code Trigger} because UST638
 * requires a Super Admin to change its interval at RUNTIME, which a property placeholder cannot do —
 * it resolves once at bean creation. Nothing asks for that here, and the dynamic machinery is not free:
 * it costs a per-fire configuration read and a hand-written trigger whose first-run and overlap
 * semantics have to be reasoned about. So this is the simple shape the other nine scheduled jobs in
 * this codebase use, and the interval is a restart-level setting.
 *
 * <h2>{@code fixedDelayString}, not {@code fixedRate} and not {@code cron}</h2>
 * {@code fixedDelay} measures from the previous run's COMPLETION, so a refresh that runs long cannot
 * have the next one queued behind it. {@code fixedRate} would schedule by wall clock and allow exactly
 * that pile-up. A cron was considered and rejected: this job has no reason to prefer particular
 * minutes, and an interval expresses "roughly hourly" more honestly than a cron expression that
 * implies a schedule somebody chose.
 *
 * <h2>Why the interval does not have to be short</h2>
 * The rollup answers "what did this role usually do from this status" over the whole register's
 * history. Hourly, a cohort with 7,528 events moves by a handful — the counts simply do not change
 * fast enough for a shorter interval to show an officer anything different, and each cycle is a scan of
 * the table every workflow transition writes to.
 *
 * <p>There is no catch-up logic and none is needed, which is the other half of why this is simpler than
 * the deadline sweep. That job acts on each overdue row, so a pod that was down must process the
 * backlog or those rows are never actioned. This one RECOMPUTES from scratch every time, so a missed
 * cycle has no residue — the next run produces exactly what it would have produced anyway.
 *
 * <h2>The kill switch lives in the service, not here</h2>
 * The task is registered unconditionally and {@code refresh()} returns immediately when
 * {@code cms.assistance.enabled} is false. Deliberately not {@code @ConditionalOnProperty}: that is
 * evaluated at context creation, so an operator flipping the flag would need a restart for the JOB
 * while the rail's own endpoints read the same property live. One switch with two different activation
 * semantics is the kind of difference nobody discovers until the rollup is mysteriously empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistanceNextActionRefreshScheduler {

    private final AssistanceNextActionRefreshService refreshService;

    /** Logged once at startup so the interval in force is visible without reading configuration. */
    @Value("${cms.assistance.next-action.refresh-interval-ms:3600000}")
    private long intervalMsForLogging;

    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * Startup delay of 2 minutes, so the first refresh does not contend with application startup —
     * which, in this module, includes several seeders writing to the same database.
     *
     * <p>No lease is taken when the feature is disabled, and none is taken when another pod holds it;
     * both are decided inside the service, which is also where the only writer to the rollup lives.
     */
    @Scheduled(
            initialDelayString = "${cms.assistance.next-action.initial-delay-ms:120000}",
            fixedDelayString = "${cms.assistance.next-action.refresh-interval-ms:3600000}")
    public void refreshRollup() {
        // The service never throws — see its javadoc. Wrapped anyway, because an exception escaping a
        // @Scheduled method is logged by Spring's error handler and the schedule continues, which is a
        // silent degradation for a job whose whole output is a table nobody looks at directly.
        try {
            refreshService.refresh();
        } catch (Exception e) {
            log.warn("Assistance next-action refresh threw unexpectedly: {}", e.toString(), e);
        }
    }

    @jakarta.annotation.PostConstruct
    void logSchedule() {
        if (assistanceEnabled) {
            log.info("Assistance next-action rollup refresh scheduled every {} ms", intervalMsForLogging);
        } else {
            log.info("Assistance next-action rollup refresh registered but INERT "
                    + "(cms.assistance.enabled is false)");
        }
    }
}
