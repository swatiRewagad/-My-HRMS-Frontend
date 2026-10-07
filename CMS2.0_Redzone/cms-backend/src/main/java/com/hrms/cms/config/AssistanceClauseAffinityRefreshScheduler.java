package com.hrms.cms.config;

import com.hrms.cms.service.AssistanceClauseAffinityRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the closure-clause affinity refresh on a fixed interval (Brief 21 §6.2).
 *
 * <h2>A plain {@code @Scheduled}, like {@link AssistanceNextActionRefreshScheduler}</h2>
 * Not a {@code SchedulingConfigurer} with a custom {@code Trigger}. That shape exists in this codebase
 * only in {@code ReDeadlineSweepScheduler}, because UST638 requires a Super Admin to change THAT
 * interval at runtime, which a property placeholder cannot do — it resolves once at bean creation.
 * Nothing asks for that here, and the dynamic machinery costs a per-fire configuration read and a
 * hand-written trigger whose first-run and overlap semantics have to be reasoned about. So this is the
 * simple shape the other scheduled jobs use and the interval is a restart-level setting.
 *
 * <h2>{@code fixedDelayString}, not {@code fixedRate} and not {@code cron}</h2>
 * {@code fixedDelay} measures from the previous run's COMPLETION, so a refresh that runs long cannot
 * have the next one queued behind it. {@code fixedRate} would schedule by wall clock and allow exactly
 * that pile-up. A cron was considered and rejected: this job has no reason to prefer particular
 * minutes, and an interval expresses "roughly every few hours" more honestly than a cron expression
 * that implies a schedule somebody chose.
 *
 * <h2>Six hours, not one — and the difference from the next-action job is the data, not the cost</h2>
 * The next-action rollup refreshes hourly because {@code COMPLAINT_TIMELINE} grows on every workflow
 * transition. This one counts CLOSURES, which are the rarest event in the register: 877 complaints
 * carry a closure clause against 17,433 timeline rows. An hourly pass would recompute an identical
 * answer five times out of six. Six hours still means a clause cited for the first time today is
 * reflected the same working day, which is as fresh as an ordering over hundreds of historical
 * closures can meaningfully be.
 *
 * <p>There is no catch-up logic and none is needed. The deadline sweep acts on each overdue row, so a
 * pod that was down must process the backlog or those rows are never actioned. This one RECOMPUTES
 * from scratch every time, so a missed cycle leaves no residue — the next run produces exactly what it
 * would have produced anyway.
 *
 * <h2>A SEPARATE scheduler bean, not another method on the next-action one</h2>
 * Spring's default scheduler pool is single-threaded, so two {@code @Scheduled} methods already
 * serialise against each other whichever class they live in — putting them together would buy nothing.
 * What separate beans buy is independent intervals, independent startup delays, and a lease name each,
 * so one job failing or being retuned cannot silently change the other's behaviour.
 *
 * <h2>The kill switch lives in the service, not here</h2>
 * The task is registered unconditionally and {@code refresh()} returns immediately when
 * {@code cms.assistance.enabled} is false. Deliberately not {@code @ConditionalOnProperty}: that is
 * evaluated at context creation, so an operator flipping the flag would need a restart for the JOB
 * while the read path reads the same property live. One switch with two different activation semantics
 * is the kind of difference nobody discovers until the rollup is mysteriously empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistanceClauseAffinityRefreshScheduler {

    private final AssistanceClauseAffinityRefreshService refreshService;

    /** Logged once at startup so the interval in force is visible without reading configuration. */
    @Value("${cms.assistance.clause-affinity.refresh-interval-ms:21600000}")
    private long intervalMsForLogging;

    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * Startup delay of 3 minutes.
     *
     * <p>Longer than the next-action job's 2, deliberately staggered rather than coincident. Both read
     * large slices of the same database at startup, which in this module already includes several
     * seeders writing to it; a minute apart means the two passes cannot contend with each other OR
     * with the seeders, and the single-threaded scheduler pool would otherwise simply queue one behind
     * the other with no visibility into which.
     *
     * <p>No lease is taken when the feature is disabled, and none when another pod holds it; both are
     * decided inside the service, which is also where the only writer to the rollup lives.
     */
    @Scheduled(
            initialDelayString = "${cms.assistance.clause-affinity.initial-delay-ms:180000}",
            fixedDelayString = "${cms.assistance.clause-affinity.refresh-interval-ms:21600000}")
    public void refreshRollup() {
        // The service never throws — see its javadoc. Wrapped anyway, because an exception escaping a
        // @Scheduled method is logged by Spring's error handler and the schedule continues, which is a
        // silent degradation for a job whose whole output is a table nobody looks at directly.
        try {
            refreshService.refresh();
        } catch (Exception e) {
            log.warn("Assistance clause-affinity refresh threw unexpectedly: {}", e.toString(), e);
        }
    }

    @jakarta.annotation.PostConstruct
    void logSchedule() {
        if (assistanceEnabled) {
            log.info("Assistance clause-affinity rollup refresh scheduled every {} ms",
                    intervalMsForLogging);
        } else {
            log.info("Assistance clause-affinity rollup refresh registered but INERT "
                    + "(cms.assistance.enabled is false)");
        }
    }
}
