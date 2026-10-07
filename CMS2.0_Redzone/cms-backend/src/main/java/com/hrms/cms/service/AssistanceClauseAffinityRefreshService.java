package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceClauseAffinity;
import com.hrms.cms.entity.AssistanceJobLock;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.AssistanceClauseAffinityRepository;
import com.hrms.cms.repository.AssistanceJobLockRepository;
import com.hrms.cms.repository.ClauseAffinitySourceRepository;
import com.hrms.cms.repository.projection.ClauseAffinityProjections.ClosureDimensions;
import com.hrms.cms.repository.projection.ClauseAffinityProjections.EntityTypeRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recomputes the closure-clause affinity rollup from past closures, on a schedule, under a lease
 * (Brief 21 §5.3.2).
 *
 * <h2>Why a job and not a query</h2>
 * §6.2's governing rule is that "rollups are computed on a schedule, never on request". The aggregate
 * this produces is a {@code GROUP BY} over every complaint that carries a closure clause, at five
 * levels of specificity; running it behind the closure form would put that work on every officer who
 * opened the Final Decision section. So it runs here, every six hours, and the request path reads one
 * cohort by its unique key.
 *
 * <h2>This is a COUNT, not a prediction</h2>
 * No inference, no model, no case text. The output is "of the N complaints closed in this situation, M
 * of them cited clause X", and the read reports it with the denominator attached. That is what keeps
 * §5.3.2 inside Tier 1 and is why the whole thing is arithmetic over seven columns.
 *
 * <h2>The SENTINEL LADDER: one closure is counted into up to five cohorts</h2>
 * The brief's key is (category, ground, entity type, resolution path). Measured, three of those four
 * are empty or near-empty on the rows that carry a clause, so keying on all four as required would ship
 * a rollup that answers almost nothing. Instead each closure is counted into a LADDER of cohorts, from
 * the most specific combination its own data supports down to (scheme, department) and
 * (scheme, {@code *}):
 * <pre>
 *   L4  scheme + department + category + ground + entityType + resolutionPath
 *   L3  scheme + department + category + ground + entityType
 *   L2  scheme + department + category + ground
 *   L1  scheme + department
 *   L0  scheme
 * </pre>
 * The read walks the same ladder downward and stops at the first level that clears the floors, so a
 * recommendation is always drawn from the most specific evidence available and never mixes two levels.
 * As {@code category_id}, {@code ground_of_complaint_id} and {@code entity_code} get populated, the
 * upper rungs start clearing the floors and the read begins preferring them — with no schema change and
 * no code change.
 *
 * <p>A level whose specific dimensions are all absent on a given closure produces the SAME key as the
 * level below it, which is correct and is why {@link #cohortsFor} de-duplicates: counting the row twice
 * under one key would double both numerator and denominator and leave the share unchanged but the
 * denominator a lie.
 *
 * <h2>What this rollup cannot say today, measured on {@code cms_db}</h2>
 * <ul>
 *   <li>877 of 4,403 complaints carry a closure clause. The rest are invisible to this rollup, which
 *       is correct — a complaint with no citation is no evidence about citations.
 *   <li>{@code ground_of_complaint_id} is populated on <b>0</b> rows, so L2/L3/L4 collapse onto their
 *       lower rungs for every closure in the database. The dimension is carried because the brief names
 *       it and because it costs nothing until it is written.
 *   <li>{@code entity_type} resolves on <b>1 of 877</b>: 876 rows hold {@code 'Test Bank Ltd'}, which
 *       matches no registered entity.
 *   <li>{@code category_id} is on <b>65 of 877</b> (7%).
 *   <li>So the cohorts that actually clear a 5-sample floor today are L1 {@code (RBIOS_2021, CEPC)} =
 *       821 closures and {@code (RBIOS_2021, RBIO)} = 55, plus L0 {@code (RBIOS_2021)} = 877. At L1
 *       CEPC the distribution is {@code 15(1)(a)} 777, {@code 16(2)(a)} 29, and {@code 15(1)(b)} /
 *       {@code 16(2)(b)} / {@code 16(3)} 5 each — which is a REAL ordering with a real denominator, and
 *       is the honest extent of what can be recommended until the finer dimensions populate.
 *   <li>The distribution is LOPSIDED because the data is seeded: one clause holds 89% of the CEPC
 *       cohort. {@link #MIN_CLAUSE_SHARE} is enforced but on this data it mostly rejects the long tail,
 *       so it has not been exercised against a realistic distribution. Said plainly rather than
 *       reported as a passing test.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistanceClauseAffinityRefreshService {

    /**
     * Closures read per page.
     *
     * <p>Keyset-paged rather than read whole, so the job's heap does not scale with the register. 2,000
     * projections of eight scalars is a few hundred kilobytes; the whole closure history at once is 877
     * rows today and unbounded later, and "it fit in dev" is how a job comes to fail only in
     * production. Matches {@code AssistanceNextActionRefreshService.PAGE_SIZE} deliberately — two jobs
     * with the same shape and different page sizes invite the reader to look for a reason there is not.
     */
    static final int PAGE_SIZE = 2_000;

    /**
     * Row cap on the one-shot read of {@code REGULATED_ENTITIES}.
     *
     * <p>145 rows exist; 2,000 is headroom and a DECLARED cap per §6.2, not a page size — this read is
     * not paged, so exceeding the cap would silently truncate the entity-type map and demote some
     * closures to the wildcard. {@link #loadEntityTypes} therefore WARNS when it comes back full, so
     * the degradation is visible rather than inferred from an odd-looking ordering months later.
     */
    static final int ENTITY_TYPE_CAP = 2_000;

    /**
     * Minimum closures in a cohort before ANY of its clauses are stored.
     *
     * <p>Five, matching {@code AssistanceNextActionRefreshService.MIN_COHORT_SAMPLE} and
     * {@code AssistanceRailService.MIN_CLOSURE_SAMPLE}, and for the same reason: an ordering drawn from
     * two closures has the authority of a statistic and the content of an anecdote, and an officer
     * cannot tell which they are looking at from a reordered dropdown. Silence — the picker in its
     * original order — is the correct degraded state.
     *
     * <p>A floor on the COHORT and not on the clause, because the denominator is the cohort: a clause
     * cited once out of 800 is a meaningful "almost never", whereas a clause cited once out of two is
     * noise about the cohort, not about the clause.
     */
    static final long MIN_COHORT_SAMPLE = 5;

    /**
     * Minimum share of its cohort a clause must hold to be stored at all.
     *
     * <p>5%. Lower than the next-action prior's 50% because the two answer different questions: that
     * one stores a single WINNER and must be able to claim "this is what usually happens", so a
     * plurality below half would make its own sentence false. This one stores an ORDERING, and a clause
     * cited in 8% of a cohort is genuinely the third-best suggestion — dropping everything below half
     * would leave most cohorts with exactly one ranked clause and nothing to order.
     *
     * <p>It is not zero, which would be the natural reading of "store the whole distribution". A clause
     * cited once in 800 closures is as likely to be a mis-citation as a precedent, and promoting it
     * above the unranked clauses in a picker would be the rollup asserting something the data does not
     * support. MEASURED on {@code cms_db}: at this floor the L1 CEPC cohort keeps {@code 15(1)(a)}
     * (89%) and {@code 16(2)(a)} (6%) and drops the three clauses cited 5 times each (0.6%).
     */
    static final double MIN_CLAUSE_SHARE = 0.05d;

    /**
     * How long a taken lease lasts.
     *
     * <p>Generous relative to the measured runtime (a pass over 877 closures is sub-second) because the
     * failure modes are asymmetric: a lease that expires while its holder is still working readmits the
     * concurrency it exists to prevent, whereas one held too long after a crash costs at most one
     * skipped cycle. Must stay comfortably BELOW the refresh interval, or a lapsed lease would still be
     * held when the next cycle came round.
     */
    static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    private final ClauseAffinitySourceRepository sourceRepository;
    private final AssistanceClauseAffinityRepository rollupRepository;
    private final AssistanceJobLockRepository lockRepository;

    /**
     * The §6.2 GLOBAL kill switch, shared with the rail and with the next-action refresh.
     *
     * <p>Defaults to {@code false} to match {@code AssistanceRailController}, so an environment that
     * never set the key does not quietly acquire a scheduled table scan.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null.
     */
    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * This feature's OWN §6.2 kill switch, {@code cms.assistance.clause-recommendation.enabled}.
     *
     * <h3>Why a second switch exists at all</h3>
     * Every other assistance surface is ambient: the rail reports counts beside a complaint and an
     * officer who ignores it loses nothing. §5.3.2 reorders a control that commits a STATUTORY closure
     * citation. Those two things do not deserve the same blast radius, and §6.2 requires "its own config
     * kill switch defaulting to the safe state" for precisely this reason.
     *
     * <h3>Why it is ANDed with {@link #assistanceEnabled} and does not replace it</h3>
     * An operator reaching for the big lever after an incident gets this feature too. A standalone flag
     * would mean the switch that was missed is the one that keeps running, which is the failure a kill
     * switch exists to prevent.
     *
     * <h3>Why the JOB honours it and not only the read</h3>
     * {@code ClosureClauseRecommendationService} checks the same pair, so turning the switch off makes
     * the picker unranked immediately. If only the read honoured it, this job would carry on scanning
     * the whole closure register every six hours to maintain a table nothing read — §6.2's requirement
     * is that the switch "stops the queries and not merely the display". Both halves reading the same
     * two keys is also what avoids the trap the next-action scheduler's javadoc names: one switch with
     * two different activation semantics is the kind of difference nobody discovers until the rollup is
     * mysteriously empty.
     *
     * <p>Note the asymmetry that remains, stated rather than hidden: turning the switch back ON makes
     * the read live before this job has run, so the picker ranks from whatever the last pass left. That
     * is correct — the rows carry their own {@code REFRESHED_AT} and a count from six hours ago is still
     * a true count — but it does mean the switch is not a way to clear the rollup.
     *
     * <p>The key is {@code cms.assistance.clause-recommendation.enabled}, the SAME spelling the read
     * path, the route {@code /clause-recommendation}, the {@code clause-recommendation.*} i18n namespace
     * and this job's own interval keys use. Two spellings for one feature is how an operator comes to
     * turn off a display while a scheduled table scan carries on.
     */
    @Value("${cms.assistance.clause-recommendation.enabled:false}")
    private boolean clauseRankingEnabled;

    /** Diagnostics only. Identifies the pod in {@code LOCKED_BY} so a lease can be traced to a holder. */
    @Value("${HOSTNAME:unknown-host}")
    private String podIdentity;

    /**
     * One refresh cycle: take the lease, recompute, release.
     *
     * <h3>Never throws</h3>
     * A scheduled task that throws is logged by Spring and then, in some configurations, silently never
     * runs again; more importantly this job has no caller to report to. So every failure is caught,
     * logged at WARN, and the lease released. The closure picker degrades to its original order when
     * the rollup is stale, empty or absent — which is the behaviour an unapplied migration relies on.
     *
     * @return the number of (cohort, clause) rows written, or 0 if the cycle did not run
     */
    public int refresh() {
        if (!assistanceEnabled || !clauseRankingEnabled) {
            // Not a warning. The switch being off is a choice, and a job that complained about it
            // every cycle would train operators to filter the log line that matters. BOTH keys are
            // named in the one line so an operator who turned one on and not the other can see which
            // from the log rather than by reading this class.
            log.debug("Assistance clause-affinity refresh skipped: cms.assistance.enabled={}, "
                    + "cms.assistance.clause-recommendation.enabled={}", assistanceEnabled,
                    clauseRankingEnabled);
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!tryAcquireLease(now)) {
            return 0;
        }

        try {
            int written = recompute(now);
            log.info("Assistance clause-affinity rollup refreshed: {} (cohort, clause) rows written",
                    written);
            return written;
        } catch (Exception e) {
            // Includes the case where the migration has not been applied — the rollup table does not
            // exist, every statement fails, and the correct outcome is a warning and an unranked picker
            // rather than a job that brings attention to itself by failing loudly every cycle.
            log.warn("Assistance clause-affinity refresh failed; the rollup is left as it was: {}",
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
     * likely cause is the lock row not existing because the migration has not been applied, and in that
     * state the job must do NOTHING. Treating an error as permission to proceed would mean the one
     * environment where the lock is unavailable is the one where every pod refreshes at once.
     */
    private boolean tryAcquireLease(LocalDateTime now) {
        try {
            int taken = lockRepository.acquire(
                    AssistanceJobLock.LOCK_NAME_CLAUSE_AFFINITY_REFRESH,
                    now,
                    now.plus(LEASE_DURATION),
                    podIdentity);
            if (taken == 0) {
                log.debug("Assistance clause-affinity refresh skipped: another pod holds the lease");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Assistance clause-affinity refresh skipped: could not take the lease ({}). "
                    + "If V116 / oracle V114 has not been applied this is expected.", e.toString());
            return false;
        }
    }

    private void releaseLease() {
        try {
            // Expired one second ago rather than exactly now, so a clock that has not advanced between
            // the release and the next acquire still satisfies the acquire's `lockedUntil <= :now`.
            lockRepository.release(AssistanceJobLock.LOCK_NAME_CLAUSE_AFFINITY_REFRESH,
                    LocalDateTime.now().minusSeconds(1));
        } catch (Exception e) {
            // Survivable by design: the lease expires on its own, so a failed release costs one idle
            // cycle rather than a stuck rollup. This is the reason it is a lease and not a flag.
            log.warn("Assistance clause-affinity lease release failed; it will expire on its own: {}",
                    e.toString());
        }
    }

    /**
     * Scans the closure history, tallies the cohort ladder, and upserts the qualifying clauses.
     *
     * <h3>UPSERT, not truncate-and-reload</h3>
     * A truncate would leave every closure picker unranked for the duration of every refresh — a
     * self-inflicted degradation on a feature whose contract is to be there when the form opens. The
     * unique key is the idempotency, so each (cohort, clause) row is found and overwritten.
     *
     * <h3>NO transaction spanning the pass, and the reason is specific</h3>
     * There is deliberately no {@code @Transactional} here, and it is not an oversight. It could not
     * work: this method is called from {@link #refresh} on {@code this}, and a self-invocation does not
     * pass through the Spring proxy, so the annotation would be INERT while reading as a guarantee —
     * the trap that is only discovered when someone relies on the rollback.
     *
     * <p>The alternative, annotating {@link #refresh} instead, is worse: it catches its own exceptions,
     * so a failed pass would mark the transaction rollback-only and the commit would then throw out of
     * a method whose whole contract is never to throw. So the one modifying query that needs a boundary
     * — {@code deleteStale} — declares {@code @Transactional} on the REPOSITORY method, where the proxy
     * IS the bean, and each upsert commits on its own via {@code SimpleJpaRepository.save}.
     *
     * <p>What that costs is cross-cohort atomicity: a reader during a refresh can see cohort A updated
     * and cohort B not yet. What it does NOT cost is the consistency that matters — numerator,
     * denominator and clause are three fields of ONE row written by ONE save, so no officer can be
     * shown a numerator from this pass beside a denominator from the last. Within a cohort the rows are
     * written consecutively from one tally, so a reader can at worst see a PREFIX of a cohort's clause
     * list: an ordering over fewer clauses, every one of which carries a true count against a true
     * denominator. An incomplete ordering is a weaker suggestion, not a wrong one.
     *
     * @param stamp the single instant every row written by this pass carries. One value for the whole
     *              pass, because the stale sweep's predicate is "older than this run" — a per-row
     *              {@code now()} would make rows written late in the pass look newer than rows written
     *              early, and the sweep could not distinguish them.
     */
    int recompute(LocalDateTime stamp) {
        Map<String, String> entityTypes = loadEntityTypes();
        Map<CohortKey, Tally> tallies = scanClosures(entityTypes);

        int written = 0;
        for (Map.Entry<CohortKey, Tally> entry : tallies.entrySet()) {
            written += upsertQualifying(entry.getKey(), entry.getValue(), stamp);
        }

        // Only after a COMPLETE pass. A failure above propagates out of this method, so the sweep is
        // never reached with a partial tally — which would delete every cohort the pass had not got to
        // yet. That ordering is the whole guarantee: there is no transaction to roll the upserts back,
        // so "do not sweep on a failed pass" is enforced by control flow and nothing else.
        // See AssistanceClauseAffinityRepository#deleteStale.
        int swept = rollupRepository.deleteStale(stamp);
        if (swept > 0) {
            log.info("Assistance clause-affinity rollup: {} rows no longer clear the floors and were "
                    + "removed", swept);
        }
        return written;
    }

    /**
     * The normalised-name to entity-type map, read once per pass.
     *
     * <p>Keyed on {@code RegulatedEntity.nameNormalized} because that is the normalisation the entity
     * applies to its own name on write, and applying the SAME function to the complaint's dirty
     * {@code entity_code} is what lets {@code 'HDFC Bank'} find {@code 'HDFC BANK'}. It will not make
     * {@code 'PNB'} find {@code 'Punjab National Bank'} — that is an alias problem, not a normalisation
     * problem, and it belongs to the entity-code data migration owned elsewhere. Unmatched codes get
     * the wildcard sentinel, so the closure still counts at the levels that do not key on entity type.
     */
    private Map<String, String> loadEntityTypes() {
        List<EntityTypeRow> rows = sourceRepository.findEntityTypes(PageRequest.of(0, ENTITY_TYPE_CAP));
        if (rows.size() >= ENTITY_TYPE_CAP) {
            log.warn("Assistance clause-affinity: the entity-type read hit its {}-row cap, so some "
                    + "closures will be counted against the wildcard entity type rather than their "
                    + "own. Raise ENTITY_TYPE_CAP.", ENTITY_TYPE_CAP);
        }
        Map<String, String> byName = new HashMap<>();
        for (EntityTypeRow row : rows) {
            // Upper-cased, like every other text dimension, so the read's already-upper-cased
            // comparison matches on both engines. See AssistanceClauseAffinity#resolutionPath.
            byName.put(row.nameNormalized().trim().toUpperCase(),
                    row.entityType().trim().toUpperCase());
        }
        return byName;
    }

    /**
     * Walks every past closure, keyset-paged, counting each into its whole cohort ladder.
     *
     * <p>A {@link LinkedHashMap} and not a {@code HashMap}, unlike the next-action prior. Iteration
     * order has no bearing on correctness — the ranking is applied on the read side and has a stated
     * tiebreak — but it makes a pass's write order reproducible, which is the difference between a
     * diagnosable job and one whose logs come out shuffled between runs over identical data.
     */
    private Map<CohortKey, Tally> scanClosures(Map<String, String> entityTypes) {
        Map<CohortKey, Tally> tallies = new LinkedHashMap<>();
        long afterId = 0L;

        while (true) {
            List<ClosureDimensions> page = sourceRepository.findClosuresForRollup(
                    afterId, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }

            for (ClosureDimensions row : page) {
                // The repository filters blank clauses, so this is non-blank. Trimmed anyway, because a
                // trailing space would key a separate clause that no read could ever match against
                // CLOSURE_CLAUSE_MASTER.
                String clause = row.closureClause().trim();
                for (CohortKey key : cohortsFor(row, entityTypes)) {
                    tallies.computeIfAbsent(key, k -> new Tally()).count(clause);
                }
            }

            afterId = page.get(page.size() - 1).complaintId();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        return tallies;
    }

    /**
     * The ladder of cohorts one closure belongs to, de-duplicated, most specific first.
     *
     * <p>DE-DUPLICATION is load-bearing, not tidiness. When a closure's category, ground and entity
     * type are all absent — the overwhelmingly common case today — L4 through L1 all reduce to the same
     * key, and counting the row four times under it would multiply both the numerator and the
     * denominator by four. The share would come out identical and the DENOMINATOR would be a lie, which
     * is precisely the number §5.1 says an officer must be able to trust. A {@link LinkedHashMap} keyed
     * on the cohort gives the de-duplication and preserves most-specific-first order.
     *
     * <p>{@code schemeVersion} is never wildcarded: a citation under one Scheme is not evidence about
     * another Scheme's clause vocabulary. A missing scheme on the complaint falls back to
     * {@link #DEFAULT_SCHEME} rather than to a wildcard, matching
     * {@code ClosureClauseAccessService.applicableSchemeVersion}, which is the authority for which
     * clause set applies to a complaint. MEASURED: 4,092 of 4,403 complaints carry no
     * {@code scheme_version}, so this fallback decides the scheme for almost every row — and it decides
     * it the same way the picker does, which is what keeps the two in agreement.
     */
    private List<CohortKey> cohortsFor(ClosureDimensions row, Map<String, String> entityTypes) {
        String scheme = normaliseText(row.schemeVersion(), DEFAULT_SCHEME);
        String department = normaliseText(row.department(), AssistanceClauseAffinity.TEXT_ANY);
        String entityType = resolveEntityType(row.entityCode(), entityTypes);
        String path = normaliseText(row.maintainabilityDetermination(),
                AssistanceClauseAffinity.TEXT_ANY);
        long category = row.categoryId() == null
                ? AssistanceClauseAffinity.NUMERIC_ANY : row.categoryId();
        long ground = row.groundOfComplaintId() == null
                ? AssistanceClauseAffinity.NUMERIC_ANY : row.groundOfComplaintId();

        Map<CohortKey, Boolean> ladder = new LinkedHashMap<>();
        // L4 → L0. Each rung drops the rightmost remaining dimension, so the sequence the read walks is
        // the reverse of this one and the two cannot disagree about what "more specific" means.
        ladder.put(new CohortKey(scheme, department, category, ground, entityType, path), true);
        ladder.put(new CohortKey(scheme, department, category, ground, entityType,
                AssistanceClauseAffinity.TEXT_ANY), true);
        ladder.put(new CohortKey(scheme, department, category, ground,
                AssistanceClauseAffinity.TEXT_ANY, AssistanceClauseAffinity.TEXT_ANY), true);
        ladder.put(new CohortKey(scheme, department, AssistanceClauseAffinity.NUMERIC_ANY,
                AssistanceClauseAffinity.NUMERIC_ANY, AssistanceClauseAffinity.TEXT_ANY,
                AssistanceClauseAffinity.TEXT_ANY), true);
        ladder.put(new CohortKey(scheme, AssistanceClauseAffinity.TEXT_ANY,
                AssistanceClauseAffinity.NUMERIC_ANY, AssistanceClauseAffinity.NUMERIC_ANY,
                AssistanceClauseAffinity.TEXT_ANY, AssistanceClauseAffinity.TEXT_ANY), true);
        return List.copyOf(ladder.keySet());
    }

    /** The scheme assumed when a complaint carries none. Matches {@code ClosureClauseAccessService}. */
    static final String DEFAULT_SCHEME = "RBIOS_2021";

    /**
     * A complaint's dirty {@code entity_code} resolved to an entity TYPE, or the wildcard.
     *
     * <p>{@code RegulatedEntity.normalize} is applied to the complaint's code — the same function the
     * entity applied to its own name — which is what makes the comparison meaningful rather than
     * literal. An unmatched code is NOT an error and is not logged per row: 876 of 877 clause-bearing
     * rows hold {@code 'Test Bank Ltd'}, so a per-row log would be 876 identical warnings per pass. The
     * gap is documented in this class's javadoc and in the migration header instead.
     */
    private String resolveEntityType(String entityCode, Map<String, String> entityTypes) {
        if (entityCode == null || entityCode.isBlank()) {
            return AssistanceClauseAffinity.TEXT_ANY;
        }
        String resolved = entityTypes.get(RegulatedEntity.normalize(entityCode));
        return resolved == null ? AssistanceClauseAffinity.TEXT_ANY : resolved;
    }

    /**
     * Trims and upper-cases a dimension, substituting {@code fallback} when it is absent.
     *
     * <p>NORMALISED ON WRITE per §6.2, and the cross-engine reason is the one recorded on
     * {@link AssistanceClauseAffinity#resolutionPath}: this database's vocabularies mix cases,
     * MySQL's collation folds comparisons for free and Oracle's does not, so a value stored verbatim
     * and compared verbatim would match in dev and miss in production. Doing it here means the read
     * never needs {@code UPPER(column)}, which would defeat the index on both engines.
     */
    private String normaliseText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase();
    }

    /**
     * Writes the clauses of one cohort that clear both floors.
     *
     * <p>A cohort below {@link #MIN_COHORT_SAMPLE} writes NOTHING — not even its dominant clause —
     * because the floor is a statement about the denominator, and a denominator of three cannot support
     * any ordering. A clause below {@link #MIN_CLAUSE_SHARE} is skipped individually while the rest of
     * its cohort is written, which is the difference between "this cohort is too thin to speak about"
     * and "this clause is too rare to promote".
     *
     * <p>Rows that fail either floor are left to the stale sweep rather than deleted here: deleting
     * would mean a per-row delete for the common case of a row that never qualified, and the sweep
     * handles the one case that matters — a row that used to qualify and no longer does.
     *
     * @return how many rows were written
     */
    private int upsertQualifying(CohortKey key, Tally tally, LocalDateTime stamp) {
        long total = tally.total();
        if (total < MIN_COHORT_SAMPLE) {
            return 0;
        }

        int written = 0;
        for (Map.Entry<String, Long> entry : tally.counts().entrySet()) {
            long occurrences = entry.getValue();
            if ((double) occurrences / (double) total < MIN_CLAUSE_SHARE) {
                log.debug("Assistance clause-affinity: clause {} rejected in cohort {} — {} of {}",
                        entry.getKey(), key, occurrences, total);
                continue;
            }

            AssistanceClauseAffinity row = rollupRepository
                    .findCohortClause(key.schemeVersion(), key.department(), key.categoryKey(),
                            key.groundKey(), key.entityType(), key.resolutionPath(), entry.getKey())
                    .orElseGet(() -> AssistanceClauseAffinity.builder()
                            .schemeVersion(key.schemeVersion())
                            .department(key.department())
                            .categoryKey(key.categoryKey())
                            .groundKey(key.groundKey())
                            .entityType(key.entityType())
                            .resolutionPath(key.resolutionPath())
                            .clauseCode(entry.getKey())
                            .build());

            row.setOccurrences(occurrences);
            row.setCohortTotal(total);
            row.setRefreshedAt(stamp);
            rollupRepository.save(row);
            written++;
        }
        return written;
    }

    /** The rollup's cohort key, as a value so it can be a map key. */
    record CohortKey(String schemeVersion,
                     String department,
                     Long categoryKey,
                     Long groundKey,
                     String entityType,
                     String resolutionPath) {
    }

    /** Clause counts for one cohort, and its denominator. */
    private static final class Tally {

        /**
         * A {@link LinkedHashMap} so the write order of a cohort's rows is reproducible across runs
         * over identical data. Correctness does not depend on it — the RANKING happens on the read side
         * and has a stated tiebreak — but a job whose log lines come out shuffled between identical
         * runs is harder to diagnose than one whose do not.
         */
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private long total;

        void count(String clause) {
            counts.merge(clause, 1L, Long::sum);
            total++;
        }

        long total() {
            return total;
        }

        Map<String, Long> counts() {
            return counts;
        }
    }
}
