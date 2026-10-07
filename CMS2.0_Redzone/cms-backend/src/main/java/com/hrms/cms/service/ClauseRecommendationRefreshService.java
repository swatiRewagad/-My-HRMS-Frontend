package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceClausePrior;
import com.hrms.cms.repository.AssistanceJobLockRepository;
import com.hrms.cms.repository.ClauseRecommendationRepository;
import com.hrms.cms.repository.ClauseRecommendationRepository.ClosedClause;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recomputes the closure-clause prior from past closures, on a schedule, under a lease (§5.3.2).
 *
 * <h2>Why a job and not a query</h2>
 * §6.2's governing rule is that "rollups are computed on a schedule, never on request". The aggregate
 * this produces is a {@code GROUP BY} plus a sort over every clause-bearing closure in the register;
 * running it behind the picker would put that on every closure screen load. So it runs here, hourly,
 * and the request path reads one index range.
 *
 * <h2>This is a COUNT, not a prediction</h2>
 * The output is "of the N closures in this cohort, M cited clause X". No inference, no model, no text.
 * That keeps the feature inside the brief's Tier 1 and is why the whole thing is arithmetic over four
 * columns.
 *
 * <h2>What the key is, and why it is not the brief's</h2>
 * §5.3.2 asks for (category, ground, entity type, resolution path). Measured against {@code cms_db} on
 * 2026-10-07: {@code ground_of_complaint_id} is populated on 0 of 4403 rows so it cannot be a key part
 * at all; {@code closure_cause}, the only column resembling "resolution path", is written in the same
 * call as the clause it would predict ({@code CepcWorkflowService:410/419},
 * {@code RbioWorkflowService:665/704}) and is present on 23 of 1422 open complaints, so it is label
 * leakage and useless at recommendation time. {@code department} replaces it. See
 * {@link AssistanceClausePrior} and V116's header for the full accounting.
 *
 * <h2>What it cannot say, measured on the dev database</h2>
 * <ul>
 *   <li>THE REGISTER HAS FIVE DISTINCT CLOSURE CLAUSES across 877 rows, and {@code 15(1)(a)} accounts
 *       for 833 of them. After the floors below, {@code 8} (cohort, clause) rows survive across
 *       {@code 6} cohorts carrying {@code 2} distinct clauses. That is the honest size of this rollup;
 *       a richer-looking table could only be produced by lowering the floors until anecdotes qualified.
 *   <li>{@code 15(1)(a)} IS RESTRICTED to {@code OMBUDSMAN,RBIO_ADMIN,ADMIN}, and §5.3.2 requires
 *       {@code restricted_to_roles} to be honoured. So the two RBIO cohorts — which contain only
 *       {@code 15(1)(a)} — are EMPTY for every other role, and an {@code RBIO_OFFICER} gets no
 *       recommendation on any of the 1238 RBIO complaints (28.1% of the register).
 *   <li>THE DISTRIBUTION IS DEGENERATE: {@code 15(1)(a)} holds 95.3% of the largest cohort and 100% of
 *       the other four. {@link #MIN_CLAUSE_SHARE} is enforced because the brief requires a floor, but
 *       on THIS data it rejects three clauses from one cohort and nothing anywhere else, so it has not
 *       been exercised against a realistic distribution. Said plainly rather than reported as a passing
 *       test.
 *   <li>2981 complaints are CLOSED and only 870 carry a clause, so 2111 closures are invisible to this
 *       rollup. They are also unappealable, which is a recorded defect elsewhere and not this job's.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClauseRecommendationRefreshService {

    /** The lease this job contends for. Must match the row seeded by V116 / oracle V114. */
    public static final String LOCK_NAME_CLAUSE_PRIOR_REFRESH = "assistance-clause-prior-refresh";

    /**
     * Closures read per keyset page.
     *
     * <p>Keyset-paged rather than read whole, so the job's heap does not scale with the register. 2,000
     * projections of five scalars is a few hundred kilobytes; the whole qualifying set at once is 870
     * today and unbounded later, and "it fit in dev" is how a job comes to fail only in production.
     * Matches {@code AssistanceNextActionRefreshService.PAGE_SIZE} deliberately — two jobs with
     * different page sizes for no stated reason is a question the next reader has to answer twice.
     */
    static final int PAGE_SIZE = 2_000;

    /**
     * Minimum closures in a cohort before ANY of its clauses are stored.
     *
     * <p>Five, matching {@code AssistanceRailService.MIN_CLOSURE_SAMPLE} and
     * {@code AssistanceNextActionRefreshService.MIN_COHORT_SAMPLE}, and for the same reason: a
     * "clauses usually cited here" drawn from two closures has the authority of a statistic and the
     * content of an anecdote, and an officer cannot tell which they are looking at from a reordered
     * dropdown. MEASURED: at this floor 6 cohorts survive covering 14 (cohort, clause) rows.
     */
    static final long MIN_COHORT_SAMPLE = 5;

    /**
     * Minimum times a clause must appear in its cohort to be ranked at all.
     *
     * <p>Three, and separate from {@link #MIN_COHORT_SAMPLE} because they guard different mistakes: the
     * cohort floor stops a whole situation being summarised from too little, while this stops a single
     * outlying closure inside a WELL-evidenced cohort from being presented as precedent. A clause cited
     * once in 815 closures is a data-entry event, not a pattern, and promoting it up a dropdown is how
     * one officer's mistake becomes fifteen officers' default.
     *
     * <p>HONESTLY: on the current data this floor rejects nothing — every clause that appears in a
     * qualifying cohort appears at least 5 times, because the seeder emitted them in blocks of 5. It is
     * correct and it is untested here.
     */
    static final long MIN_CLAUSE_OCCURRENCES = 3;

    /**
     * Minimum share of its cohort a clause must hold to be ranked.
     *
     * <p>Two percent, which is deliberately far lower than the next-action rollup's 50%, and the
     * difference is a difference in what is being claimed. That rollup asserts "this is what usually
     * happens", so a plurality below half makes the sentence false. This one asserts "these are the
     * clauses this situation has actually used, most-used first" — a second or third clause holding 3%
     * is a TRUE and useful thing to surface to an officer choosing between fifteen options, and
     * demanding half would collapse every ranking to a single item and make the feature a winner
     * announcement rather than a reordering.
     *
     * <p>It is not zero, because a long tail of 0.1% clauses would push the genuinely-common ones down
     * a list the officer reads top-first. MEASURED at this floor: 8 rows survive of the 14 that clear
     * the occurrence floor; raising it to 5% leaves 6 rows and one clause per cohort, which is the
     * collapse just described.
     */
    static final double MIN_CLAUSE_SHARE = 0.02d;

    /**
     * How many clauses are kept per cohort, best-evidenced first.
     *
     * <p>Five of fifteen master clauses. The deliverable is a REORDERING of an existing
     * {@code <select>}, not a replacement of it — every clause the role may cite stays in the list and
     * stays selectable, and this constant decides only how many carry evidence in front of them.
     * Capping matters for a reason beyond query size: a "ranking" that annotated twelve of fifteen
     * options would be noise with an ordering, and the officer would learn to ignore the annotation,
     * which costs more than the feature is worth. It also makes {@code MAX_CANDIDATE_ROWS = 20} on the
     * repository an arithmetic bound (4 cohorts x 5) rather than a guess.
     */
    static final int MAX_RANKED_PER_COHORT = 5;

    /**
     * How long a taken lease lasts.
     *
     * <p>Generous relative to the measured runtime (the qualifying scan is 870 rows) because the
     * failure modes are asymmetric: a lease that expires while its holder is still working readmits the
     * concurrency it exists to prevent, whereas one held too long after a crash costs at most one
     * skipped cycle. Must stay comfortably BELOW the refresh interval, or a lapsed lease would still be
     * held when the next cycle came round.
     */
    static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    private final ClauseRecommendationRepository priorRepository;
    private final AssistanceJobLockRepository lockRepository;

    /**
     * This feature's §6.2 kill switch, independent of the rail's.
     *
     * <p>A SEPARATE key from {@code cms.assistance.enabled}, which is the deliberate part. §6.2 says
     * "every feature has an independent config kill switch", and these are different features with
     * different risk: the rail is an ambient panel beside the screen, whereas this one reorders a
     * control that commits a real closure. An operator who needs to stop influencing closure decisions
     * must be able to do that without also blinding the rail, and the reverse.
     *
     * <p>Defaults to {@code false} — the SAFE state here is "do not influence the picker", and an
     * environment that never set the key must not quietly acquire both an hourly scan and an opinion
     * about which clause to cite.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null.
     */
    @Value("${cms.assistance.clause-recommendation.enabled:false}")
    private boolean clauseRecommendationEnabled;

    /** Diagnostics only. Identifies the pod in {@code LOCKED_BY} so a lease traces to a holder. */
    @Value("${HOSTNAME:unknown-host}")
    private String podIdentity;

    /**
     * One refresh cycle: take the lease, recompute, release.
     *
     * <h3>Never throws</h3>
     * A scheduled task that throws is logged by Spring and in some configurations silently never runs
     * again; more importantly this job has no caller to report to. So every failure is caught, logged,
     * and the lease released. The picker degrades to its existing alphabetical order when the rollup is
     * stale, empty or absent, which is the behaviour an unapplied migration already relies on.
     *
     * @return the number of (cohort, clause) rows written, or 0 if the cycle did not run
     */
    public int refresh() {
        if (!clauseRecommendationEnabled) {
            // Not a warning. The switch being off is a choice, and a job that complained about it every
            // hour would train operators to filter the log line that matters.
            log.debug("Clause-recommendation refresh skipped: "
                    + "cms.assistance.clause-recommendation.enabled is false");
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!tryAcquireLease(now)) {
            return 0;
        }

        try {
            int written = recompute(now);
            log.info("Clause-recommendation rollup refreshed: {} cohort-clause rows written", written);
            return written;
        } catch (Exception e) {
            // Includes the case where V116 has not been applied — the table does not exist, every
            // statement fails, and the correct outcome is a warning and an unchanged table rather than
            // a job that brings attention to itself by failing loudly every hour.
            log.warn("Clause-recommendation refresh failed; the rollup is left as it was: {}",
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
     * likely cause is the lock table or this job's lease ROW not existing because a migration has not
     * been applied, and in that state the job must do NOTHING. Treating an error as permission to
     * proceed would mean the one environment where the lock is unavailable is the one where every pod
     * refreshes at once.
     *
     * <p>Contends for {@link #LOCK_NAME_CLAUSE_PRIOR_REFRESH}, NOT the next-action job's lease. Sharing
     * one row would make two unrelated jobs mutually exclusive: whichever fired first would hold it and
     * the other would log "another pod holds the lease" forever, which is both false and the hardest
     * kind of bug to see — a job that is merely never running.
     */
    private boolean tryAcquireLease(LocalDateTime now) {
        try {
            int taken = lockRepository.acquire(
                    LOCK_NAME_CLAUSE_PRIOR_REFRESH,
                    now,
                    now.plus(LEASE_DURATION),
                    podIdentity);
            if (taken == 0) {
                log.debug("Clause-recommendation refresh skipped: another pod holds the lease");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Clause-recommendation refresh skipped: could not take the lease ({}). "
                    + "If V116 / oracle V114 has not been applied this is expected.", e.toString());
            return false;
        }
    }

    private void releaseLease() {
        try {
            // Expired one second ago rather than exactly now, so a clock that has not advanced between
            // the release and the next acquire still satisfies the acquire's `lockedUntil <= :now`.
            lockRepository.release(LOCK_NAME_CLAUSE_PRIOR_REFRESH,
                    LocalDateTime.now().minusSeconds(1));
        } catch (Exception e) {
            // Survivable by design: the lease expires on its own, so a failed release costs one idle
            // cycle rather than a stuck rollup. This is the reason it is a lease and not a flag.
            log.warn("Clause-recommendation lease release failed; it will expire on its own: {}",
                    e.toString());
        }
    }

    /**
     * Scans past closures, tallies clauses per cohort, and upserts the survivors.
     *
     * <h3>UPSERT, not truncate-and-reload</h3>
     * A truncate would leave the picker unranked for the duration of every refresh — a self-inflicted
     * outage on a feature whose contract is to be there when the screen loads. The unique key is the
     * idempotency, so each (cohort, clause) row is found and overwritten.
     *
     * <h3>NO transaction spanning the pass, and the reason is specific</h3>
     * There is deliberately no {@code @Transactional} here, and it is not an oversight. It could not
     * work: this method is called from {@link #refresh} on {@code this}, and a self-invocation does not
     * pass through the Spring proxy, so the annotation would be INERT while reading as a guarantee —
     * the trap that is only discovered when someone relies on the rollback. Annotating {@link #refresh}
     * instead is worse: it catches its own exceptions, so a failed pass would mark the transaction
     * rollback-only and the commit would then throw out of a method whose whole contract is never to
     * throw. The modifying sweep therefore carries its own boundary on the repository method, where the
     * proxy IS the bean.
     *
     * <p>What that costs is cross-cohort atomicity: a reader during a refresh can see one cohort
     * updated and another not yet. What it does NOT cost is the consistency that matters — numerator,
     * denominator and clause are three fields of ONE row written by ONE save, so no officer can be
     * shown a numerator from this pass beside a denominator from the last.
     *
     * @param stamp the single instant every row written by this pass carries. One value for the whole
     *              pass, because the stale sweep's predicate is "older than this run" — a per-row
     *              {@code now()} would make rows written late look newer than rows written early and
     *              the sweep could not distinguish them.
     */
    int recompute(LocalDateTime stamp) {
        Map<CohortKey, Tally> tallies = scanClosures();

        int written = 0;
        for (Map.Entry<CohortKey, Tally> entry : tallies.entrySet()) {
            written += upsertQualified(entry.getKey(), entry.getValue(), stamp);
        }

        // Only after a COMPLETE pass. A failure above propagates out of this method, so the sweep is
        // never reached with a partial tally — which would delete every cohort the pass had not got to
        // yet. That ordering is the whole guarantee: there is no transaction to roll the upserts back,
        // so "do not sweep on a failed pass" is enforced by control flow and nothing else.
        int swept = priorRepository.deleteStale(stamp);
        if (swept > 0) {
            log.info("Clause-recommendation rollup: {} cohort-clause rows no longer clear the floors "
                    + "and were removed", swept);
        }
        return written;
    }

    /**
     * Walks the qualifying closures, keyset-paged, counting clauses per cohort.
     *
     * <p>Each closure is counted into up to FOUR cohorts: its own (category, entity) pair, the two
     * one-sentinel fallbacks, and the fully-agnostic cohort. That is what makes the sentinels work —
     * the specific rows carry precision where the data supports it, and the agnostic row guarantees the
     * read has something to fall back to.
     *
     * <p>Counting one closure four times is NOT double-counting, because the four cohorts are four
     * separate questions with four separate denominators. "Of closures in CEPC, how many cited X" and
     * "of closures in CEPC against this entity, how many cited X" are both true statements about the
     * same closure, and each row carries the denominator belonging to its own question. The failure
     * would be summing across cohorts, which nothing does.
     */
    private Map<CohortKey, Tally> scanClosures() {
        Map<CohortKey, Tally> tallies = new HashMap<>();
        long afterId = 0L;

        while (true) {
            List<ClosedClause> page = priorRepository.findClosuresForRollup(
                    afterId, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }

            for (ClosedClause row : page) {
                // The repository filters blanks on department and clause, so these are non-blank.
                // Trimmed anyway, because a trailing space would key a separate cohort that no read
                // could ever reach — the read looks up the complaint's own department, which carries no
                // stray whitespace.
                String department = row.department().trim();
                String clause = row.closureClause().trim();

                Long category = row.categoryId();
                String entity = normaliseEntity(row.entityCode());

                // The fully-agnostic cohort always. Then each additional precision the row can support.
                tally(tallies, department, AssistanceClausePrior.CATEGORY_AGNOSTIC,
                        AssistanceClausePrior.ENTITY_AGNOSTIC, clause);
                if (category != null) {
                    tally(tallies, department, category, AssistanceClausePrior.ENTITY_AGNOSTIC, clause);
                }
                if (entity != null) {
                    tally(tallies, department, AssistanceClausePrior.CATEGORY_AGNOSTIC, entity, clause);
                }
                if (category != null && entity != null) {
                    tally(tallies, department, category, entity, clause);
                }
            }

            afterId = page.get(page.size() - 1).id();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        return tallies;
    }

    private void tally(Map<CohortKey, Tally> tallies, String department, Long categoryKey,
                       String entityKey, String clause) {
        tallies.computeIfAbsent(new CohortKey(department, categoryKey, entityKey), k -> new Tally())
                .count(clause);
    }

    /**
     * Normalises a dirty {@code entity_code} to the key the read will look up, or null if unusable.
     *
     * <p>{@code UPPER(TRIM(...))} ON WRITE, which is the §6.2-compliant half of the problem: the rule
     * is "normalise on write, not on read", because {@code UPPER(TRIM(c.entityCode))} in a predicate is
     * what already defeats {@code idx_complaint_entity_code} elsewhere in this codebase
     * ({@code ComplaintRepository:94}). Writing the normalised form means the request-path lookup is a
     * plain equality on an indexed column.
     *
     * <p>What it does NOT fix: {@code entity_code} is documented dirty, with the same entity appearing
     * as both {@code 'Punjab National Bank'} and {@code 'PNB'}. Those remain two cohorts. Fixing that
     * globally is a data-migration project owned elsewhere and is deliberately not attempted here; the
     * dirt is handled where it is read and reported rather than silently corrected.
     *
     * @return the normalised key, or null when there is no usable entity — in which case the closure
     *         counts toward the entity-agnostic cohorts only. Null rather than the sentinel, so the
     *         caller cannot accidentally tally the agnostic cohort twice for one closure.
     */
    private String normaliseEntity(String entityCode) {
        if (entityCode == null) {
            return null;
        }
        String normalised = entityCode.trim().toUpperCase();
        if (normalised.isEmpty() || AssistanceClausePrior.ENTITY_AGNOSTIC.equals(normalised)) {
            // A literal '*' in the source column would collide with the sentinel and silently merge a
            // real entity into the agnostic cohort. Verified absent today; refused rather than trusted.
            return null;
        }
        return normalised;
    }

    /**
     * Writes one cohort's surviving clauses, if the cohort clears its floor.
     *
     * <p>A cohort or clause that fails a floor is NOT written, and any previous row is left to the stale
     * sweep. Deleting here instead would mean a per-row delete for the common case of a clause that has
     * never qualified, and the sweep handles the one case that matters — a row that used to qualify and
     * no longer does.
     *
     * @return how many rows were written for this cohort
     */
    private int upsertQualified(CohortKey key, Tally tally, LocalDateTime stamp) {
        long total = tally.total();
        if (total < MIN_COHORT_SAMPLE) {
            return 0;
        }

        List<Tally.Entry> ranked = tally.ranked(total);
        int written = 0;
        for (Tally.Entry candidate : ranked) {
            if (written >= MAX_RANKED_PER_COHORT) {
                break;
            }

            AssistanceClausePrior row = priorRepository
                    .findCohortClause(key.department(), key.categoryKey(), key.entityKey(),
                            candidate.clause())
                    .orElseGet(() -> AssistanceClausePrior.builder()
                            .department(key.department())
                            .categoryKey(key.categoryKey())
                            .entityKey(key.entityKey())
                            .clauseCode(candidate.clause())
                            .build());

            row.setOccurrences(candidate.count());
            row.setCohortTotal(total);
            row.setRefreshedAt(stamp);
            priorRepository.save(row);
            written++;
        }
        return written;
    }

    /** The rollup's cohort key, as a value so it can be a map key. */
    private record CohortKey(String department, Long categoryKey, String entityKey) {
    }

    /** Clause counts for one cohort, and the survivors in rank order. */
    static final class Tally {

        private final Map<String, Long> counts = new HashMap<>();
        private long total;

        void count(String clause) {
            counts.merge(clause, 1L, Long::sum);
            total++;
        }

        long total() {
            return total;
        }

        /**
         * The clauses that clear both per-clause floors, most-cited first.
         *
         * <p>The tiebreak on clause code is not cosmetic. {@code HashMap} iteration order is
         * unspecified, so without it two runs over identical data could rank two equally-cited clauses
         * differently and the picker would appear to change its mind for no reason. Alphabetical is
         * arbitrary but STABLE, which is the property that matters.
         */
        List<Entry> ranked(long cohortTotal) {
            List<Entry> survivors = new ArrayList<>();
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                long count = entry.getValue();
                if (count < MIN_CLAUSE_OCCURRENCES) {
                    continue;
                }
                if ((double) count / (double) cohortTotal < MIN_CLAUSE_SHARE) {
                    continue;
                }
                survivors.add(new Entry(entry.getKey(), count));
            }
            survivors.sort(Comparator.comparingLong(Entry::count).reversed()
                    .thenComparing(Entry::clause));
            return survivors;
        }

        record Entry(String clause, long count) {
        }
    }
}
