package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceJobLock;
import com.hrms.cms.entity.AssistanceNextAction;
import com.hrms.cms.repository.AssistanceJobLockRepository;
import com.hrms.cms.repository.AssistanceNextActionRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.projection.AssistanceRailProjections.TimelineAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recomputes the next-action rollup from the timeline, on a schedule, under a lease (Brief 21 §5.3.1).
 *
 * <h2>Why a job and not a query</h2>
 * §6.2's governing rule is that "rollups are computed on a schedule, never on request". The aggregate
 * this produces is a {@code GROUP BY} over 17,433 timeline rows; running it behind the rail endpoint
 * would put that scan on every staff screen load. So it runs here, hourly, and the request path reads
 * one row by its unique key.
 *
 * <h2>This is a COUNT, not a prediction</h2>
 * No inference, no model, no text. The output is "of the N times this role acted from this status, M
 * of them did X", and the rail reports it with the denominator attached. That keeps the feature inside
 * the brief's Tier 1 and is why the whole thing is arithmetic over four columns.
 *
 * <h2>The rollup is ADVISORY and the read side must keep it that way</h2>
 * It is mined from what HAPPENED, not from what is permitted. The RBIO machine's from-status rules are
 * advertisement only, so this table can report a historically-common action the workflow would now
 * refuse. §5.1's "suggest, highlight, do not auto-select" is therefore a correctness requirement, not
 * a UX preference — {@code workflow-action-bar} commits real transitions.
 *
 * <h2>What it cannot say, measured on the dev database</h2>
 * <ul>
 *   <li>{@code performed_by_role} is populated on 7,531 of 17,433 rows (43%). The other 57% is
 *       invisible, because the role is half the key, and it is not backfillable — {@code performed_by}
 *       holds free text and spells the system actor three different ways.
 *   <li>Only 722 of 16,989 joinable rows reach a complaint carrying a category, which is why
 *       {@link AssistanceNextAction#CATEGORY_AGNOSTIC} exists. As categories get populated the
 *       specific rows start clearing the floors and the read begins preferring them, with no schema or
 *       code change.
 *   <li>The winners are LOPSIDED because the data is seeded: of 34 cohorts clearing a 5-sample floor,
 *       33 have a winner above 50%. {@link #MIN_CONFIDENCE} is enforced but on this data rejects
 *       almost nothing, so it has not been exercised against a realistic distribution. Said plainly
 *       rather than reported as a passing test.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistanceNextActionRefreshService {

    /**
     * Timeline rows read per page.
     *
     * <p>Keyset-paged rather than read whole, so the job's heap does not scale with the register. 2,000
     * projections of five scalars is a few hundred kilobytes; the whole table at once would be 17,433
     * today and unbounded later, and "it fit in dev" is how a job comes to fail only in production.
     */
    static final int PAGE_SIZE = 2_000;

    /**
     * Minimum events in a cohort before it is stored at all.
     *
     * <p>Five, matching {@link AssistanceRailService#MIN_CLOSURE_SAMPLE}, and for the same reason: a
     * "most likely next action" drawn from two events has the authority of a statistic and the content
     * of an anecdote, and an officer cannot tell which they are looking at from the rail. MEASURED: at
     * this floor 34 cohorts survive covering 7,528 events; at a floor of 2 the count roughly doubles
     * with cohorts nobody should act on.
     */
    static final long MIN_COHORT_SAMPLE = 5;

    /**
     * Minimum share of its cohort the winning action must hold.
     *
     * <p>Required by the brief. Half, because below that the phrase "most officers did X" is false —
     * the plurality winner of a genuinely split cohort is not what usually happens, and reporting it
     * as the suggestion would be the rail asserting something the data does not support.
     *
     * <p>HONESTLY: on the current seeded data this floor rejects one cohort out of 34. It is correct
     * and it is untested against a realistic distribution.
     */
    static final double MIN_CONFIDENCE = 0.5d;

    /**
     * How long a taken lease lasts.
     *
     * <p>Generous relative to the measured runtime (the full scan is seconds, not minutes) because the
     * failure modes are asymmetric: a lease that expires while its holder is still working readmits
     * the concurrency it exists to prevent, whereas one held too long after a crash costs at most one
     * skipped cycle. Must stay comfortably BELOW the refresh interval, or a lapsed lease would still be
     * held when the next cycle came round.
     */
    static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    private final ComplaintTimelineRepository timelineRepository;
    private final AssistanceNextActionRepository rollupRepository;
    private final AssistanceJobLockRepository lockRepository;

    /**
     * The §6.2 kill switch, shared with the rail endpoints.
     *
     * <p>One switch for the whole feature rather than a second one for the job: a rollup refreshing
     * behind a disabled rail is work nobody can see the result of, and a disabled refresh behind an
     * enabled rail would serve counts that silently stopped moving. Defaults to {@code false} to match
     * {@code AssistanceRailController}, so an environment that never set the key does not quietly
     * acquire an hourly table scan.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null.
     */
    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /** Diagnostics only. Identifies the pod in {@code LOCKED_BY} so a lease can be traced to a holder. */
    @Value("${HOSTNAME:unknown-host}")
    private String podIdentity;

    /**
     * One refresh cycle: take the lease, recompute, release.
     *
     * <h3>Never throws</h3>
     * A scheduled task that throws is logged by Spring and then silently never runs again in some
     * configurations; more importantly this job has no caller to report to. So every failure is caught,
     * logged, and the lease released. The rail degrades by exactly one signal when the rollup is stale
     * or empty, which is the behaviour an unapplied migration already relies on.
     *
     * @return the number of cohorts written, or 0 if the cycle did not run
     */
    public int refresh() {
        if (!assistanceEnabled) {
            // Not a warning. The switch being off is a choice, and a job that complained about it every
            // hour would train operators to filter the log line that matters.
            log.debug("Assistance next-action refresh skipped: cms.assistance.enabled is false");
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!tryAcquireLease(now)) {
            return 0;
        }

        try {
            int written = recompute(now);
            log.info("Assistance next-action rollup refreshed: {} cohorts written", written);
            return written;
        } catch (Exception e) {
            // Includes the case where the migration has not been applied — the rollup table does not
            // exist, every statement fails, and the correct outcome is a warning and an empty table
            // rather than a job that brings attention to itself by failing loudly every hour.
            log.warn("Assistance next-action refresh failed; the rollup is left as it was: {}",
                    e.toString());
            return 0;
        } finally {
            releaseLease();
        }
    }

    /**
     * Wins or loses the lease, in one statement, and never throws.
     *
     * <p>A failure here is treated as "did not get the lock", which is the safe reading: the most
     * likely cause is the lock table not existing because the migration has not been applied, and in
     * that state the job must do NOTHING. Treating an error as permission to proceed would mean the one
     * environment where the lock is unavailable is the one where every pod refreshes at once.
     */
    private boolean tryAcquireLease(LocalDateTime now) {
        try {
            int taken = lockRepository.acquire(
                    AssistanceJobLock.LOCK_NAME_NEXT_ACTION_REFRESH,
                    now,
                    now.plus(LEASE_DURATION),
                    podIdentity);
            if (taken == 0) {
                log.debug("Assistance next-action refresh skipped: another pod holds the lease");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Assistance next-action refresh skipped: could not take the lease ({}). "
                    + "If V115 / oracle V113 has not been applied this is expected.", e.toString());
            return false;
        }
    }

    private void releaseLease() {
        try {
            // Expired one second ago rather than exactly now, so a clock that has not advanced between
            // the release and the next acquire still satisfies the acquire's `lockedUntil <= :now`.
            lockRepository.release(AssistanceJobLock.LOCK_NAME_NEXT_ACTION_REFRESH,
                    LocalDateTime.now().minusSeconds(1));
        } catch (Exception e) {
            // Survivable by design: the lease expires on its own, so a failed release costs one idle
            // cycle rather than a stuck rollup. This is the reason it is a lease and not a flag.
            log.warn("Assistance next-action lease release failed; it will expire on its own: {}",
                    e.toString());
        }
    }

    /**
     * Scans the timeline, tallies cohorts, and upserts the winners.
     *
     * <h3>UPSERT, not truncate-and-reload</h3>
     * A truncate would leave the rail silent for the duration of every refresh — a self-inflicted
     * outage on a feature whose contract is to be there when the screen loads. The unique key is the
     * idempotency, so each cohort's row is found and overwritten.
     *
     * <h3>NO transaction spanning the pass, and the reason is specific</h3>
     * There is deliberately no {@code @Transactional} here, and it is not an oversight. It could not
     * work: this method is called from {@link #refresh} on {@code this}, and a self-invocation does not
     * pass through the Spring proxy, so the annotation would be INERT while reading as a guarantee —
     * the trap that is only discovered when someone relies on the rollback.
     *
     * <p>The alternative, annotating {@link #refresh} instead, is worse: it catches its own exceptions,
     * so a failed pass would mark the transaction rollback-only and then the commit would throw out of
     * a method whose whole contract is never to throw.
     *
     * <p>So each cohort commits on its own, via {@code SimpleJpaRepository.save}'s own transaction.
     * What that costs is cross-cohort atomicity: a reader during a refresh can see cohort A updated and
     * cohort B not yet. What it does NOT cost is the consistency that actually matters — numerator,
     * denominator and action are three fields of ONE row written by ONE save, so no officer can ever be
     * shown a numerator from this pass beside a denominator from the last. Cohorts are independent
     * facts about different situations, so a reader seeing them refreshed at slightly different
     * instants is reading two true statements, not one torn one.
     *
     * @param stamp the single instant every row written by this pass carries. One value for the whole
     *              pass, because the stale sweep's predicate is "older than this run" — a per-row
     *              {@code now()} would make rows written late in the pass look newer than rows written
     *              early, and the sweep could not distinguish them.
     */
    int recompute(LocalDateTime stamp) {
        Map<CohortKey, Tally> tallies = scanTimeline();

        int written = 0;
        for (Map.Entry<CohortKey, Tally> entry : tallies.entrySet()) {
            if (upsertIfQualified(entry.getKey(), entry.getValue(), stamp)) {
                written++;
            }
        }

        // Only after a COMPLETE pass. A failure above propagates out of this method, so the sweep is
        // never reached with a partial tally — which would delete every cohort the pass had not got to
        // yet. That ordering is the whole guarantee: there is no transaction to roll the upserts back,
        // so "do not sweep on a failed pass" is enforced by control flow and nothing else.
        // See AssistanceNextActionRepository#deleteStale.
        int swept = rollupRepository.deleteStale(stamp);
        if (swept > 0) {
            log.info("Assistance next-action rollup: {} cohorts no longer clear the floors and were "
                    + "removed", swept);
        }
        return written;
    }

    /**
     * Walks the whole timeline, keyset-paged, counting actions per cohort.
     *
     * <p>Each qualifying event is counted TWICE: once into its category-specific cohort (when the
     * complaint has a category) and once into the category-agnostic cohort. That is what makes the
     * sentinel work — the specific row carries category precision where the data supports it, and the
     * agnostic row guarantees the read has something to fall back to.
     */
    private Map<CohortKey, Tally> scanTimeline() {
        Map<CohortKey, Tally> tallies = new HashMap<>();
        long afterId = 0L;

        while (true) {
            List<TimelineAction> page = timelineRepository.findActionsForRollup(
                    afterId, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }

            for (TimelineAction row : page) {
                // The repository filters blanks, so these are non-blank. Trimmed anyway, because a
                // trailing space would key a separate cohort that no read could ever reach — the rail
                // looks up the complaint's status, which carries no stray whitespace.
                String status = row.fromStatus().trim();
                String role = row.performedByRole().trim();
                String action = row.action().trim();

                tallies.computeIfAbsent(
                        new CohortKey(status, role, AssistanceNextAction.CATEGORY_AGNOSTIC),
                        k -> new Tally()).count(action);

                if (row.categoryId() != null) {
                    tallies.computeIfAbsent(new CohortKey(status, role, row.categoryId()),
                            k -> new Tally()).count(action);
                }
            }

            afterId = page.get(page.size() - 1).timelineId();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        return tallies;
    }

    /**
     * Writes one cohort, if it clears both floors.
     *
     * <p>A cohort that fails either floor is NOT written and any previous row for it is left to the
     * stale sweep. Deleting it here instead would mean a per-cohort delete for the common case of a
     * cohort that has never qualified, and the sweep handles the one case that matters — a cohort that
     * used to qualify and no longer does.
     *
     * @return true if a row was written
     */
    private boolean upsertIfQualified(CohortKey key, Tally tally, LocalDateTime stamp) {
        long total = tally.total();
        if (total < MIN_COHORT_SAMPLE) {
            return false;
        }

        Tally.Winner winner = tally.winner();
        if (winner == null) {
            return false;
        }
        if ((double) winner.count() / (double) total < MIN_CONFIDENCE) {
            log.debug("Assistance next-action cohort ({}, {}, cat {}) rejected: winner {} holds only "
                            + "{} of {}", key.fromStatus(), key.role(), key.categoryKey(),
                    winner.action(), winner.count(), total);
            return false;
        }

        AssistanceNextAction row = rollupRepository
                .findCohort(key.fromStatus(), key.role(), key.categoryKey())
                .orElseGet(() -> AssistanceNextAction.builder()
                        .fromStatus(key.fromStatus())
                        .performedByRole(key.role())
                        .categoryKey(key.categoryKey())
                        .build());

        row.setAction(winner.action());
        row.setOccurrences(winner.count());
        row.setCohortTotal(total);
        row.setRefreshedAt(stamp);
        rollupRepository.save(row);
        return true;
    }

    /** The rollup's key, as a value so it can be a map key. */
    private record CohortKey(String fromStatus, String role, Long categoryKey) {
    }

    /** Action counts for one cohort, and the winner among them. */
    private static final class Tally {

        private final Map<String, Long> counts = new HashMap<>();
        private long total;

        void count(String action) {
            counts.merge(action, 1L, Long::sum);
            total++;
        }

        long total() {
            return total;
        }

        /**
         * The most frequent action, ties broken by name.
         *
         * <p>The tiebreak is not cosmetic. {@code HashMap} iteration order is unspecified, so without
         * it two runs over identical data could store different winners and the rail would appear to
         * change its advice for no reason. Alphabetical is arbitrary but STABLE, which is the property
         * that matters — and a tie that reaches here has already cleared the confidence floor, so both
         * candidates are defensible.
         */
        Winner winner() {
            String bestAction = null;
            long bestCount = -1;
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                long count = entry.getValue();
                if (count > bestCount
                        || (count == bestCount && entry.getKey().compareTo(bestAction) < 0)) {
                    bestAction = entry.getKey();
                    bestCount = count;
                }
            }
            return bestAction == null ? null : new Winner(bestAction, bestCount);
        }

        record Winner(String action, long count) {
        }
    }
}
