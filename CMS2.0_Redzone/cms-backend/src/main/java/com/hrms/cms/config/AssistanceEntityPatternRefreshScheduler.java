package com.hrms.cms.config;

import com.hrms.cms.service.AssistanceEntityPatternRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the entity-pattern rollup refresh on a fixed interval (Brief 21 §5.3.5, §6.2).
 *
 * <h2>Same shape as {@link AssistanceNextActionRefreshScheduler}, deliberately</h2>
 * {@code fixedDelayString} rather than {@code fixedRate} (so a long refresh cannot have the next one
 * queued behind it), a plain {@code @Scheduled} rather than a {@code SchedulingConfigurer} with a
 * runtime-mutable {@code Trigger} (nothing asks for runtime interval changes here — see that class's
 * javadoc for why {@code ReDeadlineSweepScheduler} needs one and this does not), and the kill switch in
 * the SERVICE rather than as {@code @ConditionalOnProperty} (which is evaluated at context creation, so
 * an operator flipping the flag would need a restart for the job while the rail's endpoints read the
 * same property live).
 *
 * <p>Three schedulers rather than one that fans out to three services: each job has its own lease row
 * and its own interval, so one class per job means a stalled job's log line names itself and an
 * operator can change one interval without reasoning about the other two.
 *
 * <h2>Its OWN interval key, and a LONGER default than the other two</h2>
 * {@code cms.assistance.entity-pattern.refresh-interval-ms}, defaulting to SIX hours rather than one.
 * The reason is measured rather than guessed: this job walks the whole complaint register (4,403 rows
 * today, all of them, with no window predicate — the quarter is part of the key, not a filter), whereas
 * the next-action job scans the timeline and the clause-affinity job a filtered subset. And the number
 * it produces moves slowly — a window holding 880 open cases does not change meaningfully in an hour,
 * so a shorter interval would buy an officer nothing and cost a full register scan six times as often.
 *
 * <p>The interval must stay comfortably ABOVE
 * {@link AssistanceEntityPatternRefreshService#LEASE_DURATION} (10 minutes). If it did not, a cycle
 * could start while the previous lease was still held, lose the race, and skip — so the rollup would
 * refresh only when the schedule and the lease expiry happened to align.
 *
 * <h2>No catch-up logic, and none is needed</h2>
 * The job RECOMPUTES from scratch every pass, so a missed cycle leaves no residue — the next run
 * produces exactly what it would have produced anyway. That is what separates this from the deadline
 * sweep, which acts on each overdue row and must therefore process a backlog.
 *
 * <p>The one thing a long outage DOES cost here is staleness of a particular kind: the rollup keeps
 * serving the last computed counts, and a stale "14 open cases" about a named institution is a FALSE
 * statement rather than merely an unhelpful suggestion. {@code REFRESHED_AT} is stored on every row so
 * that is diagnosable; nothing in the request path refuses to serve on age, because a signal that
 * vanished on a schedule nobody could see would be worse than one an operator can check.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistanceEntityPatternRefreshScheduler {

    private final AssistanceEntityPatternRefreshService refreshService;

    /** Logged once at startup so the interval in force is visible without reading configuration. */
    @Value("${cms.assistance.entity-pattern.refresh-interval-ms:21600000}")
    private long intervalMsForLogging;

    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * Startup delay of 3 minutes — a minute longer than the next-action job's.
     *
     * <p>Staggered on purpose. Both jobs start from the same instant on a pod restart, and both open a
     * scan against the same database; the leases are separate rows so they do NOT exclude each other,
     * which means an unstaggered start would put two register-wide scans and a batch of seeders on one
     * connection pool at the one moment the application is least able to absorb it.
     */
    @Scheduled(
            initialDelayString = "${cms.assistance.entity-pattern.initial-delay-ms:180000}",
            fixedDelayString = "${cms.assistance.entity-pattern.refresh-interval-ms:21600000}")
    public void refreshRollup() {
        // The service never throws — see its javadoc. Wrapped anyway, because an exception escaping a
        // @Scheduled method is logged by Spring's error handler and the schedule continues, which is a
        // silent degradation for a job whose whole output is a table nobody looks at directly.
        try {
            refreshService.refresh();
        } catch (Exception e) {
            log.warn("Assistance entity-pattern refresh threw unexpectedly: {}", e.toString(), e);
        }
    }

    @jakarta.annotation.PostConstruct
    void logSchedule() {
        if (assistanceEnabled) {
            log.info("Assistance entity-pattern rollup refresh scheduled every {} ms",
                    intervalMsForLogging);
        } else {
            log.info("Assistance entity-pattern rollup refresh registered but INERT "
                    + "(cms.assistance.enabled is false)");
        }
    }
}
