package com.hrms.cms.config;

import com.hrms.cms.service.ReDeadlineSweepService;
import com.hrms.cms.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Duration;
import java.time.Instant;

/**
 * Runs the RE-deadline sweep on an interval a Super Admin can change at runtime (UST638).
 *
 * <h2>Why this could not be an @Scheduled annotation</h2>
 * Every other scheduled job in this codebase — nine in {@code NotificationScheduledTasks},
 * {@code ReActivityScheduler}, {@code AaEscalationScheduler}, {@code SlaCheckScheduler} and the rest — is
 * a static {@code @Scheduled(cron = "${...}")}. A property placeholder is resolved ONCE when the bean is
 * created, so changing it requires a restart with new configuration. UST638 requires the interval to be
 * changeable at runtime, which a property cannot do however it is written.
 *
 * <p>A custom {@link Trigger} is therefore used, with {@code nextExecution} consulting
 * SYSTEM_CONFIG on every fire. There was no dynamic scheduler anywhere in any module to copy — verified by
 * searching all of them for {@code SchedulingConfigurer}, custom {@code Trigger} and
 * {@code ThreadPoolTaskScheduler} — so this is the first, and it is deliberately narrow: one job, one key.
 *
 * <h2>Why not just an enabled flag on a fast fixed cron</h2>
 * That was the cheaper option and it is NOT compliant: it changes WHETHER the job runs, not how often. An
 * operator asked to reduce chasing frequency from hourly to daily cannot express that with a flag, and
 * running hourly-but-mostly-skipping still wakes the pod and queries the database every hour.
 *
 * <h2>Bounds, and why the interval is clamped rather than trusted</h2>
 * A zero or negative interval would spin the executor continuously against a shared database; an
 * enormous one would silently switch the sweep off while appearing configured. Both are clamped to a
 * sane range and the clamp is logged, so a mistyped value is visible rather than either catastrophic or
 * inert.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ReDeadlineSweepScheduler implements SchedulingConfigurer {

    /** Minutes between sweeps. Admin-editable; read on every fire, so a change needs no restart. */
    public static final String CFG_SWEEP_MINUTES = "re.deadline.sweep_interval_minutes";

    private static final long DEFAULT_MINUTES = 30;
    private static final long MIN_MINUTES = 1;
    private static final long MAX_MINUTES = 1440;

    /** Delay before the first sweep, so it does not contend with application startup. */
    private static final Duration STARTUP_DELAY = Duration.ofSeconds(20);

    private final ReDeadlineSweepService sweepService;
    private final SystemConfigService systemConfigService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(sweepService::sweepOverdueDeadlines, new ConfigurableIntervalTrigger());
        log.info("RE-deadline sweep registered with a runtime-configurable interval (key {}, default {} min)",
                CFG_SWEEP_MINUTES, DEFAULT_MINUTES);
    }

    /**
     * A trigger that re-reads its interval from configuration before scheduling each run.
     *
     * <p>{@link Trigger} is implemented directly rather than subclassing {@code PeriodicTrigger}, whose period
     * is fixed at construction in this Spring version — there is no setter to re-point, so a subclass could
     * not actually change its own interval.
     *
     * <p>{@code nextExecution} is called by Spring after every completion, which is exactly the hook needed:
     * a new value is picked up on the following cycle rather than at startup. {@link SystemConfigService}
     * caches for 30 seconds, so a change is live within that and no pod needs restarting.
     */
    private class ConfigurableIntervalTrigger implements Trigger {

        @Override
        public Instant nextExecution(TriggerContext context) {
            if (context.lastCompletion() == null) {
                // FIRST run after startup: shortly from now, not a full interval away. Scheduling the first
                // pass an interval out means a freshly started pod ignores every deadline that lapsed while
                // it was down — with a daily interval, a whole day of overdue entities would go unflagged.
                // A short delay rather than immediately, so the sweep does not compete with the rest of
                // application startup for the connection pool.
                return Instant.now().plus(STARTUP_DELAY);
            }
            // Thereafter measured from the last COMPLETION, not the last scheduled time, so a sweep that
            // runs long cannot queue overlapping passes against the same rows.
            return context.lastCompletion().plus(Duration.ofMinutes(resolveMinutes()));
        }

        private long resolveMinutes() {
            long configured = systemConfigService.getLong(CFG_SWEEP_MINUTES, DEFAULT_MINUTES);
            if (configured < MIN_MINUTES) {
                log.warn("{} is {} minutes, below the {}-minute floor — a shorter interval would spin against "
                                + "a shared database. Using {}.",
                        CFG_SWEEP_MINUTES, configured, MIN_MINUTES, MIN_MINUTES);
                return MIN_MINUTES;
            }
            if (configured > MAX_MINUTES) {
                log.warn("{} is {} minutes, above the {}-minute ceiling — a longer interval would look "
                                + "configured while effectively disabling the sweep. Using {}.",
                        CFG_SWEEP_MINUTES, configured, MAX_MINUTES, MAX_MINUTES);
                return MAX_MINUTES;
            }
            return configured;
        }
    }
}
