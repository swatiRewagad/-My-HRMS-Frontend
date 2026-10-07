package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceEntityPattern;
import com.hrms.cms.entity.AssistanceJobLock;
import com.hrms.cms.repository.AssistanceEntityPatternRepository;
import com.hrms.cms.repository.AssistanceJobLockRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.projection.AssistanceRailProjections.EntityPatternRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recomputes the entity-pattern rollup from the complaint register, on a schedule, under a lease
 * (Brief 21 §5.3.5).
 *
 * <h2>Why a job and not a query, in the brief's own words</h2>
 * §5.3.5 is the one item in §5.3 where the requirement is stated twice: "from a scheduled rollup
 * only... Never computed live." §6.2's governing rule says the same thing generally.
 *
 * <p>And the reason is NOT that the live query is unaffordable. The earlier note here claimed
 * {@code entity_code} carried no index and planned as {@code type=ALL}; that is FALSE and has been
 * corrected — {@code idx_complaint_entity_code} exists and the live aggregate plans as
 * {@code type=ref, rows=155} on {@code cms_db}. It is forbidden rather than slow: the rail renders on
 * EVERY staff screen load, so a {@code GROUP BY} here is a {@code GROUP BY} on the critical path of
 * every page in the product, multiplied by every signal that later gets added beside it. This class
 * moves that work hours away from any officer, and the request path then reads tens of rows by a
 * unique key.
 *
 * <h2>This is a COUNT, and the distinction is not pedantry</h2>
 * No inference, no model, no text, and no judgement. The output is "of the M complaints filed against
 * this entity in this department this quarter, K are still open", and the rail reports it with the
 * denominator attached. An officer reading that as a pre-judgement of the complaint in front of them
 * would be reading something the data does not say, which is why both this class and the rail's prose
 * are phrased as a report about the REGISTER'S OWN BACKLOG rather than as a finding against the entity.
 *
 * <h2>THE DEPARTMENT IS PART OF THE KEY, AND THAT IS A DISCLOSURE CONTROL</h2>
 * §5.3.5 is a CROSS-COMPLAINT disclosure: it tells an officer about OTHER complainants' live cases. So
 * no cross-department total is computed — not merely not served, not COMPUTED — because Brief 21 §4
 * requires the restrictive default until a human rules otherwise. The schema holds no such row, this
 * job writes no such row, and {@code AssistanceEntityPatternRepository} exposes no finder that omits
 * the department. Three independent parts of one fence; removing any is a disclosure change and needs
 * a ruling, not a code review.
 *
 * <p>Note what this costs, stated rather than buried: an entity with cases in two departments is
 * counted twice, once per department, and NEITHER officer is shown the entity's true total. That is the
 * restriction working as intended, not a defect — see the findings' open ask 3.
 *
 * <h2>"Open" is resolved ONCE PER PASS from the status master</h2>
 * Through {@link RbioStatusVocabulary}, not from a literal list. There were previously two hardcoded
 * copies of the closed-status vocabulary in this codebase and they DISAGREED — one held six values, the
 * other four, omitting {@code adjudicated} and {@code conciliated}, so a complaint closed by an award
 * counted as open to one of them. Resolved once rather than per row because the vocabulary is read from
 * a table and 4,113 lookups per pass would be an N+1 against eight rows.
 *
 * <p>MEASURED on {@code cms_db}: the closed vocabulary is exactly eight values
 * ({@code adjudicated, closed, conciliated, forwarded_external, forwarded_regulator, rejected,
 * resolved, withdrawn}), and 927 of the 4,113 entity-bearing complaints are open under it.
 *
 * <h2>What it cannot say, measured on cms_db</h2>
 * <ul>
 *   <li><b>The ground dimension is ENTIRELY absent.</b> {@code ground_of_complaint_id} is populated on
 *       <b>0 of 4,403</b> complaints, so the brief's "on this ground" cannot be keyed at all today.
 *       Every row this job writes therefore carries
 *       {@link AssistanceEntityPattern#GROUND_AGNOSTIC}. The dimension is kept in the key so that the
 *       day the column starts being written the signal sharpens with no migration — see
 *       {@link #tallyInto}.
 *   <li><b>The quarter is a CALENDAR quarter, with a cliff.</b> {@link #quarterKey} is {@code yyyyQ}.
 *       At 00:00 on 1 January the signal resets and reports a small number about an entity with many
 *       live cases, because most of them were filed in December. That is the brief's own wording
 *       ("this quarter") and it is implemented literally; the cost is recorded as an open ask rather
 *       than silently redesigned. MEASURED: the register's quarters are 20261=26 / 20263=2,442 /
 *       20264=1,645 rows, so the boundary currently splits this data roughly in half.
 *   <li><b>The cohorts are dominated by seeded entities.</b> Of 53 (entity, quarter, department)
 *       cohorts holding at least one open case, 13 clear the floor and those 13 hold 880 of the 927
 *       open cases. {@code TEST BANK LTD} alone accounts for most of that. {@link #MIN_OPEN_CASES} is
 *       enforced but has NOT been exercised against a realistic distribution of entities. Said plainly
 *       rather than reported as coverage.
 *   <li><b>The alias table is finite.</b> A new abbreviation typed into a complaint tomorrow is a new
 *       entity to this job and will quietly SPLIT that entity's count between two keys — two cohorts
 *       where there should be one, each with a smaller numerator, both possibly under the floor so the
 *       signal just goes quiet. That is the cost of refusing to guess; it is the right trade, and it is
 *       a cost.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistanceEntityPatternRefreshService {

    /**
     * Complaints read per page.
     *
     * <p>Keyset-paged rather than read whole, so the job's heap does not scale with the register. 2,000
     * projections of seven scalars is a few hundred kilobytes; the whole register at once is 4,113
     * keyable rows today and unbounded later, and "it fit in dev" is how a job comes to fail only in
     * production. Matches {@code AssistanceNextActionRefreshService.PAGE_SIZE} deliberately — two jobs
     * with different page sizes for no stated reason is a question the next reader has to answer twice.
     */
    static final int PAGE_SIZE = 2_000;

    /**
     * Minimum OPEN cases in a window before it is stored at all.
     *
     * <p>Five, matching {@link AssistanceRailService#MIN_CLOSURE_SAMPLE} and
     * {@code AssistanceNextActionRefreshService.MIN_COHORT_SAMPLE}, for the same reason: "this entity
     * has 2 open cases" is not a pattern, it is an anecdote, and an officer cannot tell the two apart
     * from the rail.
     *
     * <p>The floor is on the NUMERATOR, not on the denominator, and that choice matters. A window of
     * 400 complaints with 1 open is not a pattern worth interrupting anyone about; a window of 6 with 6
     * open is. The signal is about live cases, so the live count is what has to clear the bar.
     *
     * <p>MEASURED COST: of 53 windows holding at least one open case, 13 clear this floor. The 40
     * suppressed windows hold 47 open cases between them — so the floor is not cosmetic, it genuinely
     * silences most of the register's entities, and that is the intended behaviour rather than a gap.
     */
    static final long MIN_OPEN_CASES = 5;

    /**
     * How long a taken lease lasts.
     *
     * <p>Generous relative to the measured runtime (the full register scan is seconds) because the
     * failure modes are asymmetric: a lease that expires while its holder is still working readmits the
     * concurrency it exists to prevent, whereas one held too long after a crash costs at most one
     * skipped cycle. Must stay comfortably BELOW the refresh interval, or a lapsed lease would still be
     * held when the next cycle came round.
     */
    static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    /** Hard ceiling on pages, so a pathological keyset cannot loop forever. See {@link #scanRegister}. */
    static final int MAX_PAGES = 1_000;

    private final ComplaintRepository complaintRepository;
    private final AssistanceEntityPatternRepository rollupRepository;
    private final AssistanceJobLockRepository lockRepository;
    private final RbioStatusVocabulary statusVocabulary;

    /**
     * The §6.2 kill switch, shared with the rail endpoints and the other rollups.
     *
     * <p>One switch for the whole feature rather than a fourth one for this job: a rollup refreshing
     * behind a disabled rail is work nobody can see the result of, and a disabled refresh behind an
     * enabled rail would serve counts that silently stopped moving — which is strictly worse here than
     * on the other priors, because a stale open-case count about a named institution is a FALSE
     * statement rather than merely an unhelpful one. Defaults to {@code false} to match
     * {@code AssistanceRailController}, so an environment that never set the key does not quietly
     * acquire a scheduled register scan.
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
     * A scheduled task that throws is logged by Spring and then, in some configurations, silently never
     * runs again; more importantly this job has no caller to report to. So every failure is caught,
     * logged, and the lease released. The rail degrades by exactly one signal when the rollup is stale
     * or empty, which is the behaviour an unapplied migration already relies on.
     *
     * @return the number of windows written, or 0 if the cycle did not run
     */
    public int refresh() {
        if (!assistanceEnabled) {
            // Not a warning. The switch being off is a CHOICE, and a job that complained about it every
            // cycle would train operators to filter the one log line that matters.
            log.debug("Assistance entity-pattern refresh skipped: cms.assistance.enabled is false");
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!tryAcquireLease(now)) {
            return 0;
        }

        try {
            int written = recompute(now);
            log.info("Assistance entity-pattern rollup refreshed: {} windows written", written);
            return written;
        } catch (Exception e) {
            // Includes the case where the migration has not been applied — the rollup table does not
            // exist, every statement fails, and the correct outcome is a warning and an empty table
            // rather than a job that brings attention to itself by failing loudly every cycle.
            log.warn("Assistance entity-pattern refresh failed; the rollup is left as it was. "
                    + "If V117 / oracle V115 has not been applied this is EXPECTED: {}", e.toString());
            return 0;
        } finally {
            releaseLease();
        }
    }

    /**
     * Wins or loses the lease, in one statement, and never throws.
     *
     * <p>A failure here is treated as "did not get the lock", which is the safe reading: the most likely
     * cause is the lock row not existing because the migration has not been applied, and in that state
     * the job must do NOTHING. Treating an error as permission to proceed would mean the one environment
     * where the lock is unavailable is the one where every pod refreshes at once.
     */
    private boolean tryAcquireLease(LocalDateTime now) {
        try {
            int taken = lockRepository.acquire(
                    AssistanceJobLock.LOCK_NAME_ENTITY_PATTERN_REFRESH,
                    now,
                    now.plus(LEASE_DURATION),
                    podIdentity);
            if (taken == 0) {
                log.debug("Assistance entity-pattern refresh skipped: another pod holds the lease");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Assistance entity-pattern refresh skipped: could not take the lease ({}). "
                    + "If V117 / oracle V115 has not been applied this is expected.", e.toString());
            return false;
        }
    }

    private void releaseLease() {
        try {
            // Expired one second ago rather than exactly now, so a clock that has not advanced between
            // the release and the next acquire still satisfies the acquire's `lockedUntil <= :now`.
            lockRepository.release(AssistanceJobLock.LOCK_NAME_ENTITY_PATTERN_REFRESH,
                    LocalDateTime.now().minusSeconds(1));
        } catch (Exception e) {
            // Survivable by design: the lease expires on its own, so a failed release costs one idle
            // cycle rather than a stuck rollup. This is the reason it is a lease and not a flag.
            log.warn("Assistance entity-pattern lease release failed; it will expire on its own: {}",
                    e.toString());
        }
    }

    /**
     * Scans the register, tallies windows, upserts the ones that clear the floor, sweeps the rest.
     *
     * <h3>NO transaction spanning the pass, and the reason is specific</h3>
     * There is deliberately no {@code @Transactional} here and it is not an oversight. It could not
     * work: this method is called from {@link #refresh} on {@code this}, and a self-invocation does not
     * pass through the Spring proxy, so the annotation would be INERT while reading as a guarantee — the
     * trap that is only discovered when someone relies on the rollback. Annotating {@link #refresh}
     * instead is worse: it catches its own exceptions, so a failed pass would mark the transaction
     * rollback-only and the commit would then throw out of a method whose whole contract is never to
     * throw. The one write that genuinely needs a boundary is the sweep, and that boundary lives on
     * {@code AssistanceEntityPatternRepository#deleteStale} where it cannot be bypassed.
     *
     * <p>So each window commits on its own via {@code SimpleJpaRepository.save}. What that costs is
     * cross-window atomicity: a reader during a refresh can see window A updated and window B not yet.
     * What it does NOT cost is the consistency that actually matters — numerator and denominator are two
     * fields of ONE row written by ONE save, so no officer can ever be shown an open count from this
     * pass beside a total from the last. "14 of 16" is always a pair that was true together, which is
     * the only invariant the officer's reading depends on.
     *
     * @param stamp the single instant every row written by this pass carries. One value for the whole
     *              pass, because the stale sweep's predicate is "older than this run" — a per-row
     *              {@code now()} would make rows written late in the pass look newer than rows written
     *              early, and the sweep could not distinguish them.
     */
    int recompute(LocalDateTime stamp) {
        Set<String> closed = closedStatusesLowerCase();
        Map<WindowKey, Tally> tallies = scanRegister(closed);

        int written = 0;
        for (Map.Entry<WindowKey, Tally> entry : tallies.entrySet()) {
            if (upsertIfQualified(entry.getKey(), entry.getValue(), stamp)) {
                written++;
            }
        }

        // ONLY after a COMPLETE pass. A failure above propagates out of this method, so the sweep is
        // never reached with a partial tally — which would delete every window the pass had not got to
        // yet. That ordering is the whole guarantee: there is no transaction to roll the upserts back,
        // so "do not sweep on a failed pass" is enforced by control flow and by nothing else.
        //
        // The sweep matters more on THIS rollup than on the next-action one, for two reasons. A window
        // can stop qualifying because its open count fell below the floor (cases got closed — good news
        // nobody should be told about as if it were still bad), and QUARTER_KEY is part of the key, so
        // rows ACCUMULATE per quarter rather than being overwritten in place. Without the sweep the
        // table would grow without limit AND the rail would keep reporting open cases that are no
        // longer open, about a named institution.
        int swept = rollupRepository.deleteStale(stamp);
        if (swept > 0) {
            log.info("Assistance entity-pattern rollup: {} windows no longer clear the floor or belong "
                    + "to a quarter that is still being counted, and were removed", swept);
        }
        return written;
    }

    /**
     * Walks the register, keyset-paged, counting open and total per window.
     *
     * <p>NO window predicate in the query, and that is a change from an earlier design. The quarter is
     * part of the KEY rather than a filter, so a single pass produces every quarter's windows at once
     * and the sweep then bounds the table. Filtering to one quarter would make the job unable to correct
     * a previous quarter's row after a late status change, and would leave the officer reading last
     * quarter's stale number for three months.
     *
     * <p>The page loop has a HARD CEILING ({@link #MAX_PAGES}) as well as the two natural exits. The
     * natural exits are sufficient when the keyset advances, and the ceiling exists for when it does
     * not: if a full page came back whose last id equalled the previous page's, the loop would re-read
     * the same page forever and the job would never release its lease. A bounded wrong answer is
     * recoverable; a scheduled job spinning on a database connection is not.
     */
    private Map<WindowKey, Tally> scanRegister(Set<String> closed) {
        Map<WindowKey, Tally> tallies = new HashMap<>();
        long afterId = 0L;

        for (int pages = 0; pages < MAX_PAGES; pages++) {
            List<EntityPatternRow> page = complaintRepository.findEntityPatternRows(
                    afterId, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }

            for (EntityPatternRow row : page) {
                tallyInto(tallies, row, closed);
            }

            // .get(size - 1), not List.getLast(): this module compiles at release 17 and the
            // sequenced-collection methods arrived in 21.
            long lastId = page.get(page.size() - 1).complaintId();
            if (page.size() < PAGE_SIZE) {
                break;
            }
            if (lastId <= afterId) {
                // Defensive and worth logging rather than silently breaking: the keyset failed to
                // advance, which means the ORDER BY or the id column is not what this loop assumes.
                log.warn("Assistance entity-pattern scan stopped: keyset did not advance past id {}",
                        afterId);
                break;
            }
            afterId = lastId;
        }
        return tallies;
    }

    /**
     * Counts ONE complaint into every window it belongs to.
     *
     * <h3>A complaint is counted up to FOUR times, and the duplication is what makes the read a seek</h3>
     * The key carries two SENTINELLED dimensions — ground and office — and a complaint is tallied into
     * the cross product of (its ground, agnostic) x (its office, agnostic). So:
     * <ul>
     *   <li>the fully agnostic window, which ALWAYS exists and is what the read falls back to;
     *   <li>the office-specific window, when {@code rbio_office_code} is set (410 of 4,403);
     *   <li>the ground-specific window, when {@code ground_of_complaint_id} is set (<b>0 of 4,403</b>);
     *   <li>and the both-specific window, which today therefore never exists.
     * </ul>
     * Pre-computing the fallbacks is the whole trick: the rail asks for the specific row and the
     * agnostic row in ONE index range and picks between what comes back, instead of issuing a second
     * query when the first misses.
     *
     * <h3>A missing DEPARTMENT drops the row entirely, and that is the restrictive default</h3>
     * The department is the TENANCY FENCE, so a complaint without one cannot be placed behind any fence.
     * The alternatives are to invent a department for it or to pool it under the {@code '*'} sentinel
     * where every officer would see it — both are disclosure decisions this job has no standing to make,
     * so the row is skipped. MEASURED: 23 of 4,403 complaints, so this silences a handful of rows and
     * never a whole entity.
     */
    private void tallyInto(Map<WindowKey, Tally> tallies, EntityPatternRow row, Set<String> closed) {
        // Normalised through the SAME function the read uses, which is what keeps the two sides of the
        // rollup asking and answering about the same key. Null only if the value was blank, which the
        // query already excludes — checked anyway, because a precondition enforced in one place and
        // relied on in another is a precondition that gets removed by someone tidying the query.
        String entityKey = AssistanceEntityAliasNormaliser.normalise(row.entityCode());
        if (entityKey == null) {
            return;
        }
        String department = normaliseScope(row.department());
        if (department == null) {
            return;
        }
        if (row.filedAt() == null) {
            // Also excluded by the query; a complaint with no filing date belongs to no quarter.
            return;
        }

        int quarter = quarterKey(row.filedAt());
        boolean open = isOpen(row.status(), closed);

        Long ground = row.groundId();
        String office = normaliseScope(row.officeCode());

        // The two agnostic axes are always counted; the specific ones only when the data supports them.
        tally(tallies, entityKey, AssistanceEntityPattern.GROUND_AGNOSTIC, quarter, department,
                AssistanceEntityPattern.SCOPE_AGNOSTIC, open);
        if (office != null) {
            tally(tallies, entityKey, AssistanceEntityPattern.GROUND_AGNOSTIC, quarter, department,
                    office, open);
        }
        if (ground != null && ground != AssistanceEntityPattern.GROUND_AGNOSTIC) {
            tally(tallies, entityKey, ground, quarter, department,
                    AssistanceEntityPattern.SCOPE_AGNOSTIC, open);
            if (office != null) {
                tally(tallies, entityKey, ground, quarter, department, office, open);
            }
        }
    }

    private void tally(Map<WindowKey, Tally> tallies, String entityKey, long groundKey, int quarterKey,
                       String department, String officeCode, boolean open) {
        tallies.computeIfAbsent(
                new WindowKey(entityKey, groundKey, quarterKey, department, officeCode),
                k -> new Tally()).count(open);
    }

    /**
     * Writes one window, if it clears the floor.
     *
     * <p>A window that fails the floor is NOT written, and any previous row for it is left to the stale
     * sweep. Deleting it here instead would mean a per-window delete for the common case of a window
     * that has never qualified — 40 of 53 on this register — and the sweep handles the one case that
     * matters: a window that used to qualify and no longer does.
     *
     * @return true if a row was written
     */
    private boolean upsertIfQualified(WindowKey key, Tally tally, LocalDateTime stamp) {
        if (tally.open() < MIN_OPEN_CASES) {
            return false;
        }

        AssistanceEntityPattern row = rollupRepository
                .findCohort(key.entityKey(), key.groundKey(), key.quarterKey(),
                        key.department(), key.officeCode())
                .orElseGet(() -> AssistanceEntityPattern.builder()
                        .entityKey(key.entityKey())
                        .groundKey(key.groundKey())
                        .quarterKey(key.quarterKey())
                        .department(key.department())
                        .officeCode(key.officeCode())
                        .build());

        row.setOpenCases(tally.open());
        row.setWindowTotal(tally.total());
        row.setRefreshedAt(stamp);
        rollupRepository.save(row);
        return true;
    }

    /**
     * The {@code yyyyQ} key for one filing instant — 2026-08-14 becomes 20263.
     *
     * <h3>Computed in Java rather than in SQL, because the two engines do not agree</h3>
     * MySQL spells it {@code QUARTER(created_at)} and Oracle {@code TO_CHAR(created_at, 'Q')}, and JPQL
     * has neither. More importantly, a quarter expression in the WHERE or GROUP BY of the scan would
     * wrap an indexed column in a function — the recorded offence §6.2 forbids. Here the arithmetic
     * happens on a value already in heap, and the key it produces is compared as a plain integer.
     *
     * <p>{@code year * 10 + q} rather than {@code year * 100 + q} or a string: four digits plus one
     * leaves a key that is ORDERABLE (20263 &lt; 20264 &lt; 20271) and still fits an {@code int} until
     * the year 214,748,364. A string key would sort correctly too but would make the column wider and
     * the comparison collation-dependent, which is exactly the class of bug the alias normaliser exists
     * to avoid elsewhere in this feature.
     */
    static int quarterKey(LocalDateTime filedAt) {
        int quarter = (filedAt.getMonthValue() - 1) / 3 + 1;
        return filedAt.getYear() * 10 + quarter;
    }

    /**
     * The closed-status vocabulary, lower-cased, resolved once per pass.
     *
     * <p>Lower-cased on the way in so {@link #isOpen} is a plain {@code Set.contains} rather than a
     * linear scan with {@code equalsIgnoreCase} per row — 4,113 rows times eight statuses is 33,000
     * string comparisons per pass, which is not expensive but is avoidable for free.
     *
     * <p>Never empty: {@link RbioStatusVocabulary} falls back to its legacy six-value list when the
     * master table is unseeded or unreachable. An empty set would mean "nothing is closed", so every
     * complaint ever filed would count as an open case against its entity — the rail would report
     * enormous, false numbers about named institutions. The fallback is what makes that unreachable.
     */
    private Set<String> closedStatusesLowerCase() {
        Set<String> closed = new HashSet<>();
        for (String status : statusVocabulary.closedStatuses()) {
            if (status != null && !status.isBlank()) {
                closed.add(status.trim().toLowerCase(Locale.ROOT));
            }
        }
        return closed;
    }

    /**
     * Whether one complaint counts toward the numerator.
     *
     * <p>"Open" is the COMPLEMENT of closed, not an inclusion list, and that is deliberate: a
     * non-terminal status added later counts as open automatically, where an inclusion list would
     * silently drop it and the numerator would go quietly wrong rather than failing.
     *
     * <p>A BLANK status counts as OPEN. Conservative on purpose — a complaint whose status cannot be
     * read has not been shown to be closed, and over-counting the numerator makes the rail say something
     * an officer can check and correct, while under-counting makes it silently omit live cases. Measured:
     * no row in {@code cms_db} has a blank status, so this branch is unreachable today and is here for
     * the data rather than for the code.
     */
    private boolean isOpen(String status, Set<String> closed) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return !closed.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * A scope key — department or office — normalised the same way the read normalises it.
     *
     * <p>Upper-cased and trimmed, with no alias table: unlike {@code entity_code}, {@code department}
     * holds exactly three values on the whole register ({@code CEPC} 2,831 / {@code RBIO} 1,223 /
     * {@code CRPC} 58 among entity-bearing rows) and has no synonyms to resolve. An alias table here
     * would be machinery with nothing to do, which is harder to maintain correctly than its absence.
     *
     * <p>Returns null for blank so the caller can distinguish "no scope" from a scope, rather than
     * conflating a missing value with the {@code '*'} sentinel — those two mean different things: the
     * sentinel is "counted across all offices", a blank is "we do not know which office".
     */
    private static String normaliseScope(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * The rollup's five-column key, as a value so it can be a map key.
     *
     * <p>{@code groundKey} is a primitive {@code long} and {@code officeCode} is never null here — both
     * carry their sentinel instead. A nullable key component would make two tallies for the same window
     * (one under null, one under the sentinel) and the upsert would then fight the unique constraint.
     */
    private record WindowKey(String entityKey, long groundKey, int quarterKey,
                             String department, String officeCode) {
    }

    /**
     * Open and total counts for one window.
     *
     * <p>Two longs rather than a list of complaints, because the rollup stores two numbers and the job
     * must not accumulate per-complaint state — the heap would then scale with the register rather than
     * with the number of windows, which is the thing keyset paging exists to prevent.
     */
    private static final class Tally {

        private long open;
        private long total;

        void count(boolean isOpen) {
            total++;
            if (isOpen) {
                open++;
            }
        }

        long open() {
            return open;
        }

        long total() {
            return total;
        }
    }
}
