package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A lease that lets exactly one pod run the assistance rollup refresh (Brief 21 §6.2).
 *
 * <h2>Why a table had to be written at all</h2>
 * §6.2 requires that in a multi-pod deployment exactly one pod runs a rollup. Neither existing
 * mechanism can do it:
 * <ul>
 *   <li>Hazelcast is embedded but its CLUSTERING is deliberately disabled, so there is no membership
 *       view and therefore no leader election.
 *   <li>ShedLock is NOT a cms-backend dependency — it belongs to {@code cms-outbox-publisher}, and
 *       the {@code SHEDLOCK} table declared in {@code oracle/V1} and {@code V4} is that service's.
 *       Borrowing it would put two services' jobs on one table with no shared owner.
 * </ul>
 * Both existing scheduled jobs in this module document the gap and simply accept duplicate work. That
 * is survivable for a sweep that is idempotent per row; it is not survivable here, because two pods
 * refreshing concurrently would interleave upserts against the same cohort keys.
 *
 * <h2>Why a LEASE and not a boolean</h2>
 * {@link #lockedUntil} is an expiry, and the lock is taken by a conditional
 * {@code UPDATE ... WHERE LOCKED_UNTIL <= :now} — the database decides the winner by reporting 1 or 0
 * affected rows, with no {@code SELECT ... FOR UPDATE} and no advisory lock. A boolean flag would be
 * simpler and has a failure mode that is worse than the problem: a pod killed mid-refresh never
 * clears it, so the rollup silently stops refreshing forever. Stale counts are indistinguishable from
 * correct ones, so nobody would notice. An expiry means the worst case is one skipped cycle.
 *
 * <h2>The row is SEEDED, not created on demand</h2>
 * A conditional UPDATE can only win a row that exists, so the migration inserts it dated
 * {@code 1970-01-01}. Letting the job insert-on-miss would mean two pods racing an INSERT and both
 * having to handle the constraint violation — the more complicated half of exactly the problem the
 * lease exists to avoid. {@link #LOCK_NAME_NEXT_ACTION_REFRESH} is that seeded key.
 *
 * <p>No {@code @Version} and no optimistic locking: the conditional UPDATE's own WHERE clause IS the
 * concurrency control, and a JPA version column would add a second, weaker one that fails with an
 * exception instead of an honest "you did not get the lock".
 */
@Entity
@Table(name = "ASSISTANCE_JOB_LOCK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistanceJobLock {

    /** The next-action rollup's lease. Must match the row seeded by V115 / oracle V113. */
    public static final String LOCK_NAME_NEXT_ACTION_REFRESH = "assistance-next-action-refresh";

    /**
     * The closure-clause affinity rollup's lease (Brief 21 §5.3.2). Seeded by V116 / oracle V114.
     *
     * <p>A SECOND ROW rather than a second table, and a distinct row rather than sharing the
     * next-action one. Sharing would serialise two unrelated jobs against each other: whichever ran
     * second would find the lease held and skip its cycle entirely, so one rollup would refresh hourly
     * and the other only when the two schedules happened to miss. Two rows in one table gives each job
     * its own mutual exclusion across pods while keeping ONE mechanism, which is what §6.2's
     * "use a DB-backed lock" asks for — a second locking mechanism is the thing to avoid, not a second
     * key under the same one.
     */
    public static final String LOCK_NAME_CLAUSE_AFFINITY_REFRESH = "assistance-clause-affinity-refresh";

    /**
     * The entity-pattern rollup's lease (Brief 21 §5.3.5). Seeded by V117 / oracle V115.
     *
     * <p>A THIRD ROW, for the reason the clause-affinity constant above records: one lease shared
     * between jobs serialises unrelated work, so whichever job ran second would find the lease held and
     * skip its cycle entirely. This job's scan is also the longest of the three — it walks all 4,403
     * complaints rather than a filtered subset — so sharing would systematically starve whichever job
     * happened to be scheduled after it.
     */
    public static final String LOCK_NAME_ENTITY_PATTERN_REFRESH = "assistance-entity-pattern-refresh";

    /**
     * The token-to-category prior's lease. Seeded by V120 / oracle V118.
     *
     * <p>A FOURTH ROW, for the reason the two constants above record. This job's source slice is the
     * SMALLEST of the four — the 273 complaints that carry a {@code category_id} — but its lease must
     * still be its own, because the failure of a shared lease is not contention, it is a job that is
     * merely never running: whichever job fired first would hold the row and the other would log
     * "another pod holds the lease", which is both false and invisible.
     */
    public static final String LOCK_NAME_CATEGORY_PRIOR_REFRESH = "assistance-category-prior-refresh";

    /**
     * The complainant filing-history projection's lease. Seeded by V121 / oracle V119.
     *
     * <p>A FIFTH ROW, for the reason the three constants above record: one lease shared between jobs
     * serialises unrelated work, and the failure is not contention but a job that is merely never
     * running — whichever fired first would hold the row and the other would log "another pod holds the
     * lease", which is both false and invisible, because its output table simply stays as it was and
     * stale rows look exactly like correct rows.
     *
     * <p>This job's pass is tied with the entity-pattern one for the LONGEST of the five, and that makes
     * a shared lease actively harmful rather than merely untidy: it walks ALL 4,403 complaints and can
     * filter none of them, because a complainant's FIRST complaint is as much a part of their filing
     * history as their tenth. Sharing with either of the two full-table jobs would systematically starve
     * whichever happened to be scheduled second.
     */
    public static final String LOCK_NAME_COMPLAINANT_HISTORY_REFRESH =
            "assistance-complainant-history-refresh";

    @Id
    @Column(name = "LOCK_NAME", nullable = false, length = 100)
    private String lockName;

    /** When the current holder's claim lapses. The whole mechanism is a comparison against this. */
    @Column(name = "LOCKED_UNTIL", nullable = false)
    private LocalDateTime lockedUntil;

    /**
     * Who holds it, and from when. DIAGNOSTICS ONLY — never a predicate.
     *
     * <p>Deliberately not part of the release condition. Releasing "only if I still hold it" sounds
     * safer and is worse: a pod whose lease expired mid-run would be unable to release, so the row
     * would stay locked until the expiry it had already passed. These two columns exist so that a
     * lease which keeps expiring mid-run names the pod it was expiring under.
     */
    @Column(name = "LOCKED_BY", length = 200)
    private String lockedBy;

    @Column(name = "LOCKED_AT")
    private LocalDateTime lockedAt;
}
