package com.hrms.cms.service;

import com.hrms.cms.dto.AaAssignmentRequest;
import com.hrms.cms.dto.AaAssignmentResult;
import com.hrms.cms.entity.AaAssignmentAudit;
import com.hrms.cms.entity.AaAssignmentCounter;
import com.hrms.cms.entity.AaAssignmentRecord;
import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.repository.AaAssignmentAuditRepository;
import com.hrms.cms.repository.AaAssignmentCounterRepository;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import com.hrms.cms.repository.AaWorkloadRepository;
import com.hrms.cms.security.AaIdentityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Places AA work with officers: real-time eligibility, per-officer thresholds, and a round-robin
 * rotation that is deterministic across a restart.
 *
 * Why this is a re-implementation and not a call into cms-workflow-service's RoundRobinAssignmentService:
 * that class lives in a different Maven module running as a separate JVM on 8083, is not on this
 * module's classpath, and is not exposed over HTTP by its own controller. Its two pool tables, however,
 * live in this same schema, so this class reuses the TABLES while fixing three defects rather than
 * inheriting them:
 *
 *   1. Its pointer was a list index applied modulo a candidate list re-sorted by workload on every
 *      call, so the stored position identified a different officer each time and the rotation was not
 *      deterministic. This class persists the last assigned USER ID instead.
 *   2. When no eligible officer was found it retried with a query that dropped the on-leave predicate,
 *      quietly assigning work to officers on leave. There is no such fallback here.
 *   3. When every officer was at capacity it assigned to the least loaded one anyway, breaching the
 *      threshold with only a log line to show for it. Here that path either resets the pointer and
 *      re-evaluates, or -- if a grace allowance is configured -- places the record and writes an audit
 *      row, and the outcome is reported distinctly either way.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaAssignmentEngineImpl implements AaAssignmentEngine {

    /** Grace allowance above threshold, in records. 0 disables it, which is the default. */
    static final String CFG_GRACE_ALLOWANCE = "cms.aa.assignment.grace_allowance";

    /** Whether a threshold change rebalances the queue automatically (story 7). */
    static final String CFG_AUTO_REBALANCE = "cms.aa.assignment.auto_rebalance";

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final AaOfficerPoolRepository poolRepository;
    private final AaAssignmentCounterRepository counterRepository;
    private final AaAssignmentCounterInitialiser counterInitialiser;
    private final AaWorkloadRepository workloadRepository;
    private final AaAssignmentAuditRepository auditRepository;
    private final SystemConfigService systemConfigService;
    private final AaIdentityResolver identityResolver;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public AaAssignmentResult assign(AaAssignmentRequest request) {
        if (request == null || isBlank(request.getAppealNumber()) || isBlank(request.getRoleGroup())) {
            throw new IllegalArgumentException("appealNumber and roleGroup are required");
        }
        String roleGroup = request.getRoleGroup().trim();

        // Take the pointer lock FIRST, before reading the pool. Everything after this point -- the
        // workload counts, the threshold comparison, the placement -- is inside the critical section
        // for this role group. The predecessor locked after reading the pool, so two callers could both
        // see an officer one below their threshold and both assign to them.
        AaAssignmentCounter counter = lockCounter(roleGroup);

        List<AaOfficerPool> pool = isBlank(request.getRegionalOffice())
                ? poolRepository.findEligible(roleGroup)
                : poolRepository.findEligibleInRegion(roleGroup, request.getRegionalOffice().trim());

        if (pool.isEmpty()) {
            log.warn("AA assignment: no active, available officer in pool {} -- {} left unassigned",
                    roleGroup, request.getAppealNumber());
            return unassigned(AaAssignmentResult.Outcome.UNASSIGNED_POOL_EMPTY, roleGroup,
                    "aa.assignment.pool_empty");
        }

        Map<String, Integer> workloads = chargeableWorkloads(pool);

        // A required language short-circuits the rotation entirely: the record can only be handled by
        // someone who reads that language, so capacity is not the deciding factor.
        if (!isBlank(request.getRequiredLanguage())) {
            AaAssignmentResult override = assignVernacular(request, roleGroup, pool, workloads);
            if (override != null) {
                return override;
            }
            // No skilled officer: fall through to ordinary rotation rather than refusing. Leaving the
            // record unassigned would strand it; a non-native handler with a translation is recoverable.
            log.info("AA assignment: no officer in {} lists language '{}' -- falling back to rotation",
                    roleGroup, request.getRequiredLanguage());
        }

        List<AaOfficerPool> withCapacity = pool.stream()
                .filter(o -> hasCapacity(o, workloads.getOrDefault(o.getUserId(), 0)))
                .toList();

        if (!withCapacity.isEmpty()) {
            AaOfficerPool chosen = selectByRotation(withCapacity, workloads, counter);
            return place(request, chosen, roleGroup, workloads, counter,
                    AaAssignmentResult.Outcome.ASSIGNED, false, SYSTEM_ACTOR, null,
                    "aa.assignment.assigned");
        }

        // Everyone is at threshold. Reset the pointer and re-evaluate, so the rotation restarts from a
        // known state instead of stalling on a stale position (story 4).
        return assignAfterExhaustion(request, roleGroup, pool, workloads, counter);
    }

    /**
     * Story 4: every officer has reached their threshold.
     *
     * The pointer is reset and eligibility re-evaluated. Because eligibility is read inside the lock,
     * a re-read cannot find new capacity that the first pass missed -- so the reset exists to make the
     * NEXT cycle start cleanly, and this cycle proceeds only if a grace allowance is configured.
     * Without one the record is reported as unassigned rather than silently forced onto someone.
     */
    private AaAssignmentResult assignAfterExhaustion(AaAssignmentRequest request,
                                                     String roleGroup,
                                                     List<AaOfficerPool> pool,
                                                     Map<String, Integer> workloads,
                                                     AaAssignmentCounter counter) {
        int grace = systemConfigService.getInt(CFG_GRACE_ALLOWANCE, 0);
        if (grace <= 0) {
            // The pointer is deliberately NOT reset here. Resetting on every exhausted attempt wrote an
            // audit row per attempt -- a flood under sustained exhaustion -- and destroyed the rotation
            // position without placing anything, so the next successful assignment restarted at the head
            // and favoured the same officer.
            log.warn("AA assignment: every officer in {} is at threshold and no grace allowance is "
                    + "configured -- {} left unassigned", roleGroup, request.getAppealNumber());
            return unassigned(AaAssignmentResult.Outcome.UNASSIGNED_POOL_EXHAUSTED, roleGroup,
                    "aa.assignment.pool_exhausted");
        }

        // Reset only when a placement is actually going to follow, so the audit row records a real
        // change of state.
        resetPointer(counter, roleGroup);

        List<AaOfficerPool> withinGrace = pool.stream()
                .filter(o -> {
                    int load = workloads.getOrDefault(o.getUserId(), 0);
                    return o.hasUnlimitedThreshold() || load < o.thresholdOrZero() + grace;
                })
                .toList();

        if (withinGrace.isEmpty()) {
            log.warn("AA assignment: pool {} exhausted even with grace allowance {} -- {} unassigned",
                    roleGroup, grace, request.getAppealNumber());
            return unassigned(AaAssignmentResult.Outcome.UNASSIGNED_POOL_EXHAUSTED, roleGroup,
                    "aa.assignment.pool_exhausted");
        }

        AaOfficerPool chosen = selectByRotation(withinGrace, workloads, counter);
        AaAssignmentResult result = place(request, chosen, roleGroup, workloads, counter,
                AaAssignmentResult.Outcome.ASSIGNED_UNDER_GRACE, false, SYSTEM_ACTOR,
                "grace allowance " + grace + " applied after pool exhaustion",
                "aa.assignment.assigned_under_grace");

        // A placement above threshold is a deviation from policy, so it is auditable rather than a
        // log line. This is the case the predecessor made invisible.
        audit(AaAssignmentAudit.ACTION_THRESHOLD_BREACH_GRACE, chosen.getUserId(), roleGroup,
                request.getAppealNumber(), "workload",
                String.valueOf(workloads.getOrDefault(chosen.getUserId(), 0)),
                String.valueOf(result.getWorkloadAfter()),
                "threshold " + chosen.thresholdOrZero() + " exceeded under configured grace allowance "
                        + grace, SYSTEM_ACTOR, null);
        return result;
    }

    /** Story 8: route to a language-skilled officer, bypassing rotation and not charging capacity. */
    private AaAssignmentResult assignVernacular(AaAssignmentRequest request,
                                                String roleGroup,
                                                List<AaOfficerPool> pool,
                                                Map<String, Integer> workloads) {
        String language = request.getRequiredLanguage().trim();
        List<AaOfficerPool> skilled = pool.stream()
                .filter(o -> o.speaks(language))
                .sorted(Comparator
                        .comparingInt((AaOfficerPool o) -> workloads.getOrDefault(o.getUserId(), 0))
                        .thenComparing(AaOfficerPool::getUserId))
                .toList();

        if (skilled.isEmpty()) {
            return null;
        }

        // Least-loaded skilled officer, user id breaking the tie. The rotation pointer is deliberately
        // NOT consulted and NOT advanced: this placement is outside the rotation, so advancing it would
        // let vernacular traffic skew the fair share of officers who never received the work.
        AaOfficerPool chosen = skilled.get(0);
        AaAssignmentResult result = place(request, chosen, roleGroup, workloads, null,
                AaAssignmentResult.Outcome.VERNACULAR_OVERRIDE, true, SYSTEM_ACTOR,
                "vernacular override for language " + language,
                "aa.assignment.vernacular_override");

        audit(AaAssignmentAudit.ACTION_VERNACULAR_OVERRIDE, chosen.getUserId(), roleGroup,
                request.getAppealNumber(), "requiredLanguage", null, language,
                "routed to a language-skilled officer, exempt from threshold", SYSTEM_ACTOR, null);
        return result;
    }

    @Override
    @Transactional
    public AaAssignmentResult assignManually(String appealNumber, String targetUserId, String reason) {
        if (isBlank(appealNumber) || isBlank(targetUserId)) {
            throw new IllegalArgumentException("appealNumber and targetUserId are required");
        }
        // A manual override bypasses the threshold, so the reason is the only record of why. Without it
        // the audit row would show a deliberate breach with no justification.
        if (isBlank(reason)) {
            throw new IllegalArgumentException("aa.assignment.error_reason_required");
        }

        String actor = identityResolver.resolveActor();
        String actorRole = identityResolver.resolveAaRole();

        AaOfficerPool target = poolRepository.findByUserId(targetUserId.trim())
                .orElseThrow(() -> new IllegalArgumentException("aa.assignment.error_target_not_in_pool"));

        // An admin may deliberately overload an officer, but may not assign work to someone who cannot
        // receive it at all -- that would strand the record with no one accountable.
        if (!target.isActive() || target.isOnLeave()) {
            throw new IllegalArgumentException("aa.assignment.error_target_unavailable");
        }

        String roleGroup = target.getRoleGroup();

        // Take the same pointer lock an ordinary assignment takes. Without it a manual placement can
        // interleave with assign() for the same appeal and leave TWO unreleased holder rows, which
        // permanently corrupts both officers' workload counts. The threshold is bypassed here
        // deliberately; the single-holder invariant is not negotiable.
        lockCounter(roleGroup);

        Map<String, Integer> workloads = chargeableWorkloads(List.of(target));

        AaAssignmentRequest request = AaAssignmentRequest.builder()
                .appealNumber(appealNumber.trim())
                .roleGroup(roleGroup)
                .build();

        AaAssignmentResult result = place(request, target, roleGroup, workloads, null,
                AaAssignmentResult.Outcome.MANUAL_OVERRIDE, false, actor, reason.trim(),
                "aa.assignment.manual_override");

        audit(AaAssignmentAudit.ACTION_MANUAL_ASSIGNMENT, target.getUserId(), roleGroup,
                appealNumber.trim(), "assignedOfficer", null, target.getUserId(),
                reason.trim(), actor, actorRole);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public int currentWorkload(String userId) {
        if (isBlank(userId)) {
            return 0;
        }
        return (int) workloadRepository.countChargeableFor(userId.trim());
    }

    /**
     * Chooses the next officer: lowest chargeable workload first, and where that ties, the officer
     * after the last one assigned in user-id order.
     *
     * The tie-break always resolves to exactly one officer. Candidates are ordered by (workload, userId)
     * -- a total order, since userId is unique within a pool -- and the pointer selects the first
     * candidate strictly after the previous holder, wrapping to the head. Because the pointer stores a
     * user id rather than a position, it keeps its meaning when the list is re-sorted by workload, when
     * officers join or leave the pool, and across a restart.
     */
    private AaOfficerPool selectByRotation(List<AaOfficerPool> candidates,
                                           Map<String, Integer> workloads,
                                           AaAssignmentCounter counter) {
        List<AaOfficerPool> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingInt((AaOfficerPool o) -> workloads.getOrDefault(o.getUserId(), 0))
                .thenComparing(AaOfficerPool::getUserId));

        String last = counter == null ? null : counter.getLastAssignedUserId();
        if (last == null || last.isBlank()) {
            return ordered.get(0);
        }

        int lowest = workloads.getOrDefault(ordered.get(0).getUserId(), 0);
        List<AaOfficerPool> tied = ordered.stream()
                .filter(o -> workloads.getOrDefault(o.getUserId(), 0) == lowest)
                .toList();

        // Only rotate among officers who are actually tied at the lowest load. If someone is strictly
        // less loaded than the rest, fairness is already served by taking them; rotating past them
        // would leave load unbalanced.
        if (tied.size() <= 1) {
            return ordered.get(0);
        }
        for (AaOfficerPool candidate : tied) {
            if (candidate.getUserId().compareTo(last) > 0) {
                return candidate;
            }
        }
        return tied.get(0);
    }

    /** Writes the placement and advances the pointer. */
    private AaAssignmentResult place(AaAssignmentRequest request,
                                     AaOfficerPool officer,
                                     String roleGroup,
                                     Map<String, Integer> workloads,
                                     AaAssignmentCounter counter,
                                     AaAssignmentResult.Outcome outcome,
                                     boolean thresholdExempt,
                                     String assignedBy,
                                     String reason,
                                     String messageKey) {
        // Releasing any previous holder keeps the "one unreleased row per appeal" invariant, which is
        // what makes the workload counts correct after a reassignment.
        //
        // Flushed immediately: the unique index that enforces the invariant is evaluated per statement,
        // so if the release were still pending in the Hibernate action queue when the new row is
        // inserted, the database would briefly see two live holders and reject a perfectly legitimate
        // reassignment.
        workloadRepository.findByAppealNumberAndReleasedAtIsNull(request.getAppealNumber())
                .ifPresent(existing -> {
                    existing.setReleasedAt(LocalDateTime.now());
                    workloadRepository.saveAndFlush(existing);
                });

        AaAssignmentRecord record = AaAssignmentRecord.builder()
                .appealNumber(request.getAppealNumber())
                .assignedUserId(officer.getUserId())
                .roleGroup(roleGroup)
                .outcome(outcome.name())
                .thresholdExempt(thresholdExempt)
                .assignedBy(assignedBy)
                .assignedAt(LocalDateTime.now())
                .build();
        try {
            // Flushed here so the single-holder unique index rejects a concurrent double assignment now,
            // with a reportable error, rather than at commit where it would surface as an opaque 500.
            // The rotation lock is per role group, so this is the only thing standing between a
            // cross-role-group race and two officers being charged for one record.
            workloadRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            log.warn("AA assignment: {} was concurrently assigned by another transaction",
                    request.getAppealNumber());
            throw new IllegalStateException("aa.assignment.error_concurrently_assigned", e);
        }

        if (counter != null) {
            counter.setLastAssignedUserId(officer.getUserId());
            counter.setUpdatedAt(LocalDateTime.now());
            counterRepository.save(counter);
        }

        int before = workloads.getOrDefault(officer.getUserId(), 0);
        int after = thresholdExempt ? before : before + 1;

        notifyAssignee(officer.getUserId(), request.getAppealNumber(), messageKey);

        return AaAssignmentResult.builder()
                .outcome(outcome)
                .assignedUserId(officer.getUserId())
                .roleGroup(roleGroup)
                .workloadAfter(after)
                .thresholdAtAssignment(officer.thresholdOrZero())
                .thresholdExempt(thresholdExempt)
                .messageKey(messageKey)
                .build();
    }

    /**
     * Story 13: tell the assignee, so their bell updates without a refresh.
     *
     * Deferred until AFTER the transaction commits. NotificationService.send is @Async on another bean,
     * so it commits its own transaction immediately: sending inline meant a later rollback left the
     * officer with a bell and a websocket push for an assignment that never happened, and a client
     * following the push would 404.
     *
     * When there is no active transaction (a direct call from a test, say) it is sent immediately.
     * Failure to notify never fails the assignment -- the work is correctly placed either way.
     */
    private void notifyAssignee(String userId, String appealNumber, String messageKey) {
        Runnable send = () -> {
            try {
                notificationService.send(userId, "ASSIGNMENT", messageKey,
                        appealNumber, appealNumber, "DRAFT", "/aa/drafts/" + appealNumber);
            } catch (Exception e) {
                log.warn("AA assignment: could not notify {} about {}: {}", userId, appealNumber,
                        e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    /** Live chargeable counts, defaulting officers with no held records to zero. */
    private Map<String, Integer> chargeableWorkloads(List<AaOfficerPool> pool) {
        List<String> userIds = pool.stream().map(AaOfficerPool::getUserId).toList();
        Map<String, Integer> counts = new HashMap<>();
        for (String userId : userIds) {
            counts.put(userId, 0);
        }
        if (userIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : workloadRepository.countChargeableGrouped(userIds)) {
            counts.put((String) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private boolean hasCapacity(AaOfficerPool officer, int currentLoad) {
        return officer.hasUnlimitedThreshold() || currentLoad < officer.thresholdOrZero();
    }

    /**
     * Locks this role group's pointer, creating it first if absent.
     *
     * Creation is delegated to a separate bean so its REQUIRES_NEW transaction is actually honoured --
     * see AaAssignmentCounterInitialiser for why doing it inline did not work. Once the row is committed
     * by that call, the lock below is taken on a row that certainly exists.
     */
    private AaAssignmentCounter lockCounter(String roleGroup) {
        // The row is guaranteed to exist BEFORE the lock is taken, unconditionally and in its own
        // committed transaction. Both halves of that matter:
        //
        //  - Locking first is wrong. A SELECT ... FOR UPDATE matching no row takes an InnoDB gap lock over
        //    the unique-index range, and the insert meant to fill that gap then blocks on the very
        //    transaction waiting for it: a self-deadlock, seen as "Lock wait timeout exceeded" on the
        //    first assignment into any new role group.
        //  - Guarding the call with an unlocked existence check is also wrong. Under REPEATABLE READ that
        //    read pins the transaction's snapshot before the lock is acquired, which was enough to let
        //    concurrent callers stop serialising -- the threshold test caught twelve placements against a
        //    capacity of six.
        //
        // ensureExists returns immediately when the row is already there, so the steady-state cost is one
        // indexed lookup.
        counterInitialiser.ensureExists(roleGroup);

        return counterRepository.findByRoleGroupForUpdate(roleGroup)
                .orElseThrow(() -> new IllegalStateException(
                        "Could not obtain assignment pointer for " + roleGroup));
    }

    private void resetPointer(AaAssignmentCounter counter, String roleGroup) {
        if (counter.getLastAssignedUserId() == null) {
            return;
        }
        String previous = counter.getLastAssignedUserId();
        counter.setLastAssignedUserId(null);
        counter.setUpdatedAt(LocalDateTime.now());
        counterRepository.save(counter);
        audit(AaAssignmentAudit.ACTION_POINTER_RESET, null, roleGroup, null,
                "lastAssignedUserId", previous, null,
                "all officers at threshold -- pointer reset so rotation resumes", SYSTEM_ACTOR, null);
    }

    private AaAssignmentResult unassigned(AaAssignmentResult.Outcome outcome, String roleGroup,
                                          String messageKey) {
        return AaAssignmentResult.builder()
                .outcome(outcome)
                .roleGroup(roleGroup)
                .thresholdExempt(false)
                .messageKey(messageKey)
                .build();
    }

    private void audit(String action, String subjectUserId, String roleGroup, String appealNumber,
                       String fieldName, String oldValue, String newValue, String reason,
                       String actor, String actorRole) {
        auditRepository.save(AaAssignmentAudit.builder()
                .action(action)
                .subjectUserId(subjectUserId)
                .roleGroup(roleGroup)
                .appealNumber(appealNumber)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(reason)
                .performedBy(actor == null ? SYSTEM_ACTOR : actor)
                .performedByRole(actorRole)
                .performedAt(LocalDateTime.now())
                .build());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
