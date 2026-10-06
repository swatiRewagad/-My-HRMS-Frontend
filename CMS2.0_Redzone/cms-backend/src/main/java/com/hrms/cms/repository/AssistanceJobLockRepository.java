package com.hrms.cms.repository;

import com.hrms.cms.entity.AssistanceJobLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The lease lock, as two conditional statements (Brief 21 §6.2).
 *
 * <h2>The database decides the winner, by counting affected rows</h2>
 * {@link #acquire} is a single {@code UPDATE} whose WHERE clause carries the expiry. Two pods issuing
 * it at the same instant serialise on the row; the first sets a new expiry, and the second's predicate
 * no longer matches, so it is told it changed 0 rows. The return value IS the answer to "did I get the
 * lock" — there is no read-then-write window for a second pod to slip into.
 *
 * <p>This is why there is no {@code findByLockName} and no save path. A {@code SELECT} followed by a
 * {@code save()} would reintroduce exactly the race the conditional UPDATE exists to remove, and an
 * available finder is an invitation to write it. {@code JpaRepository} still exposes
 * {@code findById}/{@code save} by inheritance — unavoidable without hand-rolling the interface — so
 * the control is that {@code AssistanceNextActionRefreshService} calls neither.
 *
 * <h2>No insert path, because the row is seeded</h2>
 * {@code V115} / {@code oracle/V113} insert the {@code assistance-next-action-refresh} row dated
 * {@code 1970-01-01}. An {@code acquire} against a MISSING row returns 0 and the job skips its cycle,
 * which is the correct behaviour for an unapplied migration: the refresh does not run, the rollup stays
 * empty, and the rail omits one signal. Insert-on-miss would mean two pods racing an INSERT and both
 * handling the constraint violation — the complicated half of the problem a lease avoids.
 */
public interface AssistanceJobLockRepository extends JpaRepository<AssistanceJobLock, String> {

    /**
     * Takes the lease if it is free, in one statement.
     *
     * @param lockName    the seeded row to contend for
     * @param now         the instant deciding whether the incumbent lease has lapsed. Passed in rather
     *                    than taken as {@code CURRENT_TIMESTAMP} so the comparison uses the SAME clock
     *                    the holder will use to stamp {@code lockedUntil}: mixing the JVM's clock and
     *                    the database's across the two halves of one lease would make the window
     *                    silently longer or shorter than configured by the clock skew between them, and
     *                    a lease that is quietly short readmits the concurrency it exists to prevent.
     * @param lockedUntil the new expiry, already {@code now + leaseDuration}
     * @param lockedBy    diagnostics; the pod identity
     * @return 1 if the lease was taken, 0 if another pod holds it or the row does not exist
     */
    // REQUIRES_NEW, and this is the load-bearing detail of the whole lock. The lease must be COMMITTED
    // before the refresh starts scanning, because an uncommitted UPDATE holds a row lock that a second
    // pod's acquire would BLOCK on rather than losing — so instead of being told "you did not get the
    // lock" it would wait out the entire refresh and then win, producing the serial duplicate run the
    // lease exists to prevent. A new transaction also means the acquire survives a rollback of the
    // refresh's own work: a failed pass must still have recorded that it ran.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("UPDATE AssistanceJobLock l SET l.lockedUntil = :lockedUntil, l.lockedBy = :lockedBy, "
            + "l.lockedAt = :now WHERE l.lockName = :lockName AND l.lockedUntil <= :now")
    int acquire(@Param("lockName") String lockName,
                @Param("now") LocalDateTime now,
                @Param("lockedUntil") LocalDateTime lockedUntil,
                @Param("lockedBy") String lockedBy);

    /**
     * Gives the lease back early by expiring it.
     *
     * <p>Unconditional on the holder, deliberately. Releasing "only if I still hold it" sounds safer
     * and is worse: a pod whose lease expired MID-RUN would be unable to release, leaving the row
     * locked for an interval it had already overrun. Since a lapsed lease is already available to
     * anyone, a release from a pod that no longer holds it cannot take anything from the new holder
     * that the expiry was not about to take anyway — at worst it shortens a window that had, by
     * definition, already elapsed.
     *
     * <p>Release is an OPTIMISATION, not a correctness requirement. Skipping it costs one idle interval
     * before the expiry frees the row, which is precisely the guarantee that makes a crashed pod
     * survivable.
     *
     * @param releasedUntil an instant in the past, so the next acquire succeeds immediately
     */
    // REQUIRES_NEW for the mirror of the acquire reason: the release must commit even when the refresh
    // transaction it was called from is rolling back. Enlisting in that transaction would mean a failed
    // pass never gives the lease back, and the next cycle waits out a full expiry for no reason.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("UPDATE AssistanceJobLock l SET l.lockedUntil = :releasedUntil "
            + "WHERE l.lockName = :lockName")
    int release(@Param("lockName") String lockName,
                @Param("releasedUntil") LocalDateTime releasedUntil);
}
