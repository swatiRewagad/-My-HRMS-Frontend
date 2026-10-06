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

    /** The one lease this module takes today. Must match the row seeded by V115 / oracle V113. */
    public static final String LOCK_NAME_NEXT_ACTION_REFRESH = "assistance-next-action-refresh";

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
