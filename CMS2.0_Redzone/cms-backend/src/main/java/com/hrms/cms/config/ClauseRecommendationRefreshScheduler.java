package com.hrms.cms.config;

import com.hrms.cms.service.ClauseRecommendationRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the closure-clause prior refresh on a fixed interval (Brief 21 §6.2).
 *
 * <h2>A plain {@code @Scheduled}, like the other scheduled jobs here</h2>
 * {@code ReDeadlineSweepScheduler} implements {@code SchedulingConfigurer} with a hand-written
 * {@code Trigger} because UST638 requires a Super Admin to change its interval at RUNTIME, which a
 * property placeholder cannot do — it resolves once at bean creation. Nothing asks for that here, and
 * the dynamic machinery is not free: it costs a per-fire configuration read and a trigger whose
 * first-run and overlap semantics have to be reasoned about separately. So this is the simple shape ten
 * other schedulers in this module use, and the interval is a restart-level setting.
 *
 * <h2>{@code fixedDelayString}, not {@code fixedRate} and not {@code cron}</h2>
 * {@code fixedDelay} measures from the previous run's COMPLETION, so a refresh that runs long cannot
 * have the next one queued behind it. {@code fixedRate} schedules by wall clock and permits exactly that
 * pile-up — on a job that holds a ten-minute lease, two overlapping fires would mean the second one
 * silently skipping every cycle. A cron was rejected because this job has no reason to prefer particular
 * minutes, and an interval states "roughly every six hours" more honestly than a cron expression that
 * implies somebody chose 03:17.
 *
 * <h2>Why SIX hours and not the next-action job's one</h2>
 * Longer than its sibling, deliberately, and the reason is in the numbers rather than in caution. This
 * rollup answers "which clauses did closures like this one cite", over the whole register's history:
 * 870 closed clause-bearing complaints today, of which the largest cohort holds 815. Hourly, that cohort
 * moves by a handful of closures and the ORDER of a two-clause ranking cannot change — refreshing more
 * often would produce an identical table and read every qualifying complaint to do it. The next-action
 * rollup is hourly because it is mined from the timeline table, which every workflow transition writes
 * to, so its cohorts genuinely move faster.
 *
 * <p>The interval must also stay comfortably ABOVE {@code LEASE_DURATION} (10 minutes), or a lease that
 * lapsed from a crashed pod would still be held when the next cycle came round. Six hours is not close.
 *
 * <p>There is no catch-up logic and none is needed. The deadline sweep acts on each overdue row, so a
 * pod that was down must process the backlog or those rows are never actioned. This job RECOMPUTES from
 * scratch every time, so a missed cycle leaves no residue — the next run produces exactly what it would
 * have produced anyway.
 *
 * <h2>The kill switch lives in the service, not here</h2>
 * The task is registered unconditionally and {@code refresh()} returns immediately when
 * {@code cms.assistance.clause-recommendation.enabled} is false. Deliberately NOT
 * {@code @ConditionalOnProperty}: that is evaluated at context creation, so an operator flipping the
 * flag would need a restart for the JOB while {@code ClauseRecommendationService} and the controller
 * read the same property live. One switch with two different activation semantics is the kind of
 * difference nobody discovers until the rollup is mysteriously empty.
 *
 * <h2>A separate lease from the next-action job, and that is load-bearing</h2>
 * {@code ClauseRecommendationRefreshService} contends for {@code assistance-clause-prior-refresh}, not
 * {@code assistance-next-action-refresh}. Sharing one row would make two unrelated jobs mutually
 * exclusive: whichever fired first would hold the lease and the other would log "another pod holds the
 * lease" forever — both false and the hardest kind of bug to see, a job that is merely never running.
 * The row is seeded by V116 / oracle V114.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClauseRecommendationRefreshScheduler {

    private final ClauseRecommendationRefreshService refreshService;

    /** Logged once at startup so the interval in force is visible without reading configuration. */
    @Value("${cms.assistance.clause-recommendation.refresh-interval-ms:21600000}")
    private long intervalMsForLogging;

    @Value("${cms.assistance.clause-recommendation.enabled:false}")
    private boolean clauseRecommendationEnabled;

    /**
     * Startup delay of three minutes, so the first refresh does not contend with application startup —
     * which in this module includes more than twenty {@code CommandLineRunner} seeders writing to the
     * same database. Longer than the next-action job's two minutes on purpose: if both jobs are enabled
     * they would otherwise take their (separate) leases in the same window and scan two large tables
     * concurrently on a cold connection pool.
     *
     * <p>No lease is taken when the feature is disabled, and none is taken when another pod holds it.
     * Both decisions are inside the service, which is also the only writer to the rollup.
     */
    @Scheduled(
            initialDelayString = "${cms.assistance.clause-recommendation.initial-delay-ms:180000}",
            fixedDelayString = "${cms.assistance.clause-recommendation.refresh-interval-ms:21600000}")
    public void refreshRollup() {
        // The service never throws — see its javadoc. Wrapped anyway, because an exception escaping a
        // @Scheduled method is swallowed by Spring's error handler while the schedule continues, which
        // is a silent degradation for a job whose only output is a table nobody looks at directly.
        try {
            refreshService.refresh();
        } catch (Exception e) {
            log.warn("Clause-recommendation refresh threw unexpectedly: {}", e.toString(), e);
        }
    }

    @jakarta.annotation.PostConstruct
    void logSchedule() {
        if (clauseRecommendationEnabled) {
            log.info("Clause-recommendation rollup refresh scheduled every {} ms", intervalMsForLogging);
        } else {
            log.info("Clause-recommendation rollup refresh registered but INERT "
                    + "(cms.assistance.clause-recommendation.enabled is false)");
        }
    }
}
