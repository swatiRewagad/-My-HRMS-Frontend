package com.hrms.cms.config;

import com.hrms.cms.service.AssistanceComplainantHistoryRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the complainant filing-history refresh on a fixed interval ({@code §6.2}).
 *
 * <h2>A plain {@code @Scheduled}, like the other assistance refresh schedulers</h2>
 * Not a {@code SchedulingConfigurer} with a custom {@code Trigger}. That shape exists in this codebase
 * only in {@code ReDeadlineSweepScheduler}, because UST638 requires a Super Admin to change THAT interval
 * at runtime, which a property placeholder cannot do — it resolves once at bean creation. Nothing asks for
 * that here, and the dynamic machinery costs a per-fire configuration read and a hand-written trigger
 * whose first-run and overlap semantics have to be reasoned about.
 *
 * <h2>{@code fixedDelayString}, not {@code fixedRate} and not {@code cron}</h2>
 * {@code fixedDelay} measures from the previous run's COMPLETION, so a refresh that runs long cannot have
 * the next one queued behind it. {@code fixedRate} would schedule by wall clock and allow exactly that
 * pile-up, which matters more for this job than for its siblings: it makes TWO full passes over the
 * register, so it is the longest-running of the five.
 *
 * <h2>Six hours, and the freshness cost is stated rather than assumed</h2>
 * Matches the clause-affinity job rather than the next-action job's hourly cadence. The next-action rollup
 * refreshes hourly because {@code COMPLAINT_TIMELINE} grows on every workflow transition; this one keys on
 * complaint FILINGS, which arrive far more slowly.
 *
 * <p>THE COST, said plainly, because it is the one real weakness of precomputing this feature: a complaint
 * filed in the last six hours is NOT YET in the projection, so the duplicate signal will not see it. For a
 * feature whose purpose is catching double-handling at intake, that is a genuine gap — the most likely
 * duplicate of a complaint filed this morning is one filed yesterday afternoon, which IS covered, but a
 * same-day pair filed two hours apart is not.
 *
 * <p>It was accepted rather than solved with a shorter interval, and the reasoning is that the gap is in
 * the SIGNAL and not in the control: nothing downstream depends on the duplicate panel being complete —
 * it blocks nothing, merges nothing and auto-selects nothing, so a missed recent duplicate costs an
 * officer a convenience and is caught on the next screen load after the next pass. Shortening the interval
 * to close it would mean two full-table scans every few minutes to improve a suggestion. The honest
 * alternative, a live query at intake, is what {@code §6.2} forbids and what the fan-out guard makes
 * impossible anyway: the guard is an aggregate over the whole register and cannot be computed per request.
 *
 * <p>The interval is configurable, so an operator who decides same-day detection matters more than the
 * scan cost can shorten it without a code change.
 *
 * <h2>A SEPARATE scheduler bean, with its own lease name</h2>
 * Spring's default scheduler pool is single-threaded, so the {@code @Scheduled} methods already serialise
 * against each other whichever class they live in — putting them together would buy nothing. What separate
 * beans buy is independent intervals, independent startup delays, and a lease name each, so one job
 * failing or being retuned cannot silently change another's behaviour. This job's lease is
 * {@code assistance-complainant-history-refresh}; sharing one with either of the other two full-table jobs
 * would mean whichever fired second logged "another pod holds the lease" and never ran.
 *
 * <h2>The kill switch lives in the service, not here</h2>
 * The task is registered unconditionally and {@code refresh()} returns immediately when either
 * {@code cms.assistance.enabled} or {@code cms.assistance.duplicate-detection.enabled} is false.
 * Deliberately not {@code @ConditionalOnProperty}: that is evaluated at context creation, so an operator
 * flipping the flag would need a restart for the JOB while the read path reads the same property live. One
 * switch with two different activation semantics is the kind of difference nobody discovers until the
 * table is mysteriously empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistanceComplainantHistoryRefreshScheduler {

    private final AssistanceComplainantHistoryRefreshService refreshService;

    /** Logged once at startup so the interval in force is visible without reading configuration. */
    @Value("${cms.assistance.duplicate-detection.refresh-interval-ms:21600000}")
    private long intervalMsForLogging;

    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    @Value("${cms.assistance.duplicate-detection.enabled:false}")
    private boolean duplicateDetectionEnabled;

    /**
     * Startup delay of 4 minutes.
     *
     * <p>Longer than the next-action job's 2 and the clause-affinity job's 3, deliberately staggered
     * rather than coincident. All of them read large slices of the same database at startup, which in this
     * module already includes several seeders writing to it; a minute apart means the passes cannot
     * contend with each other OR with the seeders. The single-threaded scheduler pool would otherwise
     * simply queue one behind the other with no visibility into which — and this job, making two full
     * passes, is the one whose queueing would be most visible as a startup stall.
     *
     * <p>No lease is taken when the feature is disabled, and none when another pod holds it; both are
     * decided inside the service, which is also where the only writer to the projection lives.
     */
    @Scheduled(
            initialDelayString = "${cms.assistance.duplicate-detection.initial-delay-ms:240000}",
            fixedDelayString = "${cms.assistance.duplicate-detection.refresh-interval-ms:21600000}")
    public void refreshProjection() {
        // The service never throws — see its javadoc. Wrapped anyway, because an exception escaping a
        // @Scheduled method is logged by Spring's error handler and the schedule continues, which is a
        // silent degradation for a job whose whole output is a table nobody looks at directly.
        try {
            refreshService.refresh();
        } catch (Exception e) {
            log.warn("Assistance complainant-history refresh threw unexpectedly: {}", e.toString(), e);
        }
    }

    @jakarta.annotation.PostConstruct
    void logSchedule() {
        if (assistanceEnabled && duplicateDetectionEnabled) {
            log.info("Assistance complainant-history projection refresh scheduled every {} ms",
                    intervalMsForLogging);
        } else {
            // BOTH keys named, so an operator who turned one on and not the other can see which from the
            // log rather than by reading the service.
            log.info("Assistance complainant-history projection refresh registered but INERT "
                            + "(cms.assistance.enabled={}, cms.assistance.duplicate-detection.enabled={})",
                    assistanceEnabled, duplicateDetectionEnabled);
        }
    }
}
