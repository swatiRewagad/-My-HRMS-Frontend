package com.hrms.cms.config;

import com.hrms.cms.service.AssistanceCategoryPriorRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the token-to-category prior refresh on a fixed interval (§6.2).
 *
 * <h2>A plain {@code @Scheduled}, like its siblings</h2>
 * Not a {@code SchedulingConfigurer} with a custom {@code Trigger}. That shape exists in this codebase
 * only in {@code ReDeadlineSweepScheduler}, because UST638 requires a Super Admin to change THAT
 * interval at runtime, which a property placeholder cannot do — it resolves once at bean creation.
 * Nothing asks for that here, and the dynamic machinery costs a per-fire configuration read and a
 * hand-written trigger whose first-run and overlap semantics have to be reasoned about.
 *
 * <h2>{@code fixedDelayString}, not {@code fixedRate} and not {@code cron}</h2>
 * {@code fixedDelay} measures from the previous run's COMPLETION, so a refresh that runs long cannot have
 * the next one queued behind it. {@code fixedRate} would schedule by wall clock and allow exactly that
 * pile-up. A cron was considered and rejected: this job has no reason to prefer particular minutes.
 *
 * <h2>Six hours, matching the clause-affinity job and NOT the hourly next-action job</h2>
 * The next-action rollup refreshes hourly because {@code COMPLAINT_TIMELINE} grows on every workflow
 * transition. This one counts CATEGORISATIONS, which are rarer than closures: MEASURED, 273 complaints
 * carry a category against 877 that carry a closure clause and 17,433 timeline rows. An hourly pass would
 * recompute an identical answer five times out of six.
 *
 * <p>There is a reason to expect that to change, and it does not change the interval. This feature's own
 * success populates {@code category_id}, so the source slice should grow as officers accept suggestions —
 * but it grows by officer actions at human speed, and a category accepted this morning being reflected
 * this afternoon is as fresh as a count over hundreds of historical classifications can meaningfully be.
 *
 * <p>No catch-up logic, and none is needed. The deadline sweep acts on each overdue row, so a pod that
 * was down must process the backlog or those rows are never actioned. This one RECOMPUTES from scratch
 * every time, so a missed cycle leaves no residue — the next run produces exactly what it would have
 * produced anyway.
 *
 * <h2>A SEPARATE scheduler bean, not another method on a sibling</h2>
 * Spring's default scheduler pool is single-threaded, so the {@code @Scheduled} methods already serialise
 * against each other whichever class they live in — putting them together would buy nothing. What
 * separate beans buy is independent intervals, independent startup delays, and a lease name each, so one
 * job failing or being retuned cannot silently change another's behaviour.
 *
 * <h2>The kill switch lives in the service, not here</h2>
 * The task is registered unconditionally and {@code refresh()} returns immediately when either switch is
 * false. Deliberately not {@code @ConditionalOnProperty}: that is evaluated at context creation, so an
 * operator flipping the flag would need a restart for the JOB while the read path reads the same property
 * live. One switch with two different activation semantics is the kind of difference nobody discovers
 * until the rollup is mysteriously empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistanceCategoryPriorRefreshScheduler {

    private final AssistanceCategoryPriorRefreshService refreshService;

    /** Logged once at startup so the interval in force is visible without reading configuration. */
    @Value("${cms.assistance.category-prior.refresh-interval-ms:21600000}")
    private long intervalMsForLogging;

    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    @Value("${cms.assistance.category-suggestion.enabled:false}")
    private boolean categorySuggestionEnabled;

    /**
     * Startup delay of 4 minutes.
     *
     * <p>Staggered one minute past the clause-affinity job's 3 and two past the next-action job's 2,
     * deliberately rather than coincident. All of them read large slices of the same database at startup,
     * which in this module already includes several seeders writing to it — and one of those seeders is
     * {@code DemoDataSeeder}, which writes the very {@code category_id} values THIS job reads. Starting
     * before it finished would mine a half-seeded corpus, and the corpus floor would then either reject
     * the pass or accept a biased one. A minute's separation also means the single-threaded scheduler
     * pool does not simply queue one behind another with no visibility into which.
     *
     * <p>No lease is taken when the feature is disabled, and none when another pod holds it; both are
     * decided inside the service, which is also where the only writer to the rollup lives.
     */
    @Scheduled(
            initialDelayString = "${cms.assistance.category-prior.initial-delay-ms:240000}",
            fixedDelayString = "${cms.assistance.category-prior.refresh-interval-ms:21600000}")
    public void refreshRollup() {
        // The service never throws — see its javadoc. Wrapped anyway, because an exception escaping a
        // @Scheduled method is logged by Spring's error handler and the schedule continues, which is a
        // silent degradation for a job whose whole output is a table nobody looks at directly.
        try {
            refreshService.refresh();
        } catch (Exception e) {
            log.warn("Assistance category-prior refresh threw unexpectedly: {}", e.toString(), e);
        }
    }

    @jakarta.annotation.PostConstruct
    void logSchedule() {
        if (assistanceEnabled && categorySuggestionEnabled) {
            log.info("Assistance category-prior rollup refresh scheduled every {} ms",
                    intervalMsForLogging);
        } else {
            // BOTH keys named, so an operator who turned one on and not the other can see which from
            // the log rather than by reading the service.
            log.info("Assistance category-prior rollup refresh registered but INERT "
                    + "(cms.assistance.enabled={}, cms.assistance.category-suggestion.enabled={})",
                    assistanceEnabled, categorySuggestionEnabled);
        }
    }
}
