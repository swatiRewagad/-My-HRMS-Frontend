package com.hrms.cms.service;

import com.hrms.cms.entity.AaAssignmentCounter;
import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.repository.AaAssignmentCounterRepository;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Cluster-safe round-robin officer assignment with a pointer that survives restarts (UST471), and
 * which never offers work to an inactive or on-leave officer (UST450).
 *
 * <h2>Why this exists</h2>
 * There were five separate in-memory round-robin implementations, none cluster-safe and all resetting
 * to the head of the list on restart:
 * <ul>
 *   <li>{@code ComplaintRoutingService} — {@code ConcurrentHashMap<String,AtomicInteger>}</li>
 *   <li>{@code RbioWorkflowService.assignByRole} — non-atomic get/put on a {@code Map<String,Integer>}</li>
 *   <li>{@code EmailSyndicationApiController.assignToNextDeo} — a static {@code AtomicInteger}</li>
 *   <li>{@code cms-ingestion-service} — a single global pointer for every pool</li>
 *   <li>{@code KeycloakRoundRobinAssignmentAdapter} — a hash of the record number, which is stable but
 *       not round-robin and offers no fairness guarantee</li>
 * </ul>
 * On multiple pods each JVM kept its own pointer, so "round robin" meant "every pod starts at officer
 * one". None of them filtered on leave, so UST450 was unimplemented wherever they were used.
 *
 * <h2>What is reused, deliberately</h2>
 * The locking mechanism is NOT reinvented here. {@link AaAssignmentCounterInitialiser} and
 * {@link AaAssignmentCounterRepository#findByRoleGroupForUpdate} are already keyed on nothing but
 * {@code roleGroup}, so they are reused as-is, together with the two hard-won ordering rules their
 * javadoc records:
 * <ol>
 *   <li>The pointer row is created in a SEPARATE bean with {@code REQUIRES_NEW}, called
 *       unconditionally BEFORE the lock. Locking a row that does not exist takes an InnoDB gap lock
 *       over the unique-index range, and the insert meant to fill that gap then blocks on the same
 *       transaction — a self-deadlock that appears as "Lock wait timeout exceeded" on the first ever
 *       assignment into a new role group.</li>
 *   <li>The lock is taken BEFORE the candidate pool is read. The predecessor locked afterwards, which
 *       left the workload snapshot outside the critical section and made the lock decorative: two
 *       callers could both see the same officer one below their threshold and both assign to them.</li>
 * </ol>
 *
 * <h2>The pointer is a user id, not an index</h2>
 * A numeric index is meaningless the moment the candidate list changes length or order — it silently
 * points at a different officer. {@code last_assigned_user_id} keeps its meaning: it is only ever used
 * in a {@code compareTo} to find "the next id after this one", so an officer who has left, gone on
 * leave or been deactivated still defines a valid position in the ordering and rotation simply resumes
 * after them. This is why the dead {@code RoundRobinPointer} entity (which stored {@code currentIndex})
 * was deleted rather than revived.
 *
 * <h2>Identity: Keycloak is the source of candidates, the pool is the eligibility filter</h2>
 * Candidates come from Keycloak role membership, NOT from {@code WF_OFFICER_POOL}, because the two
 * vocabularies do not currently agree: the pool is seeded with {@code rbio.officer1..4} while the
 * realm's actual {@code RBIO_OFFICER} members are {@code rbio.officer}, {@code rbio_officer_002},
 * {@code rbio.officer.mum} and so on — zero overlap, verified against the live realm. Assigning from
 * the pool alone would hand complaints to accounts that cannot log in.
 *
 * <p>So the pool is consulted only to EXCLUDE: an officer with a pool row that says inactive or
 * on-leave is skipped. An officer with no pool row at all is eligible, because absence of a row is
 * absence of evidence rather than evidence of unavailability, and refusing to assign anyone merely
 * because the pool is unseeded would stall registration everywhere. Once the pool is reconciled with
 * the realm this becomes a strict allow-list — see the seeding migration for that decision.
 *
 * <h2>Adoption path for the remaining in-memory counters</h2>
 * {@code ComplaintRoutingService} is already migrated. The other two live in files owned by other
 * concurrent sessions and were deliberately NOT edited here; each funnels through a single method, so
 * adoption is a one-line change whenever their owner is ready:
 *
 * <ul>
 *   <li><b>{@code RbioWorkflowService.assignByRole(String role)}</b> — replace the body with
 *       {@code Assignment a = assigner.assignNext(role); return a.isAssigned() ? a.officerId() : null;}
 *       The existing {@code String}-or-{@code null} signature already matches this service's contract,
 *       and the {@code ROUND_ROBIN} branch of {@code applyAssignee} is its only caller, so every
 *       table-driven transition with {@code ASSIGN_STRATEGY='ROUND_ROBIN'} converts at once.
 *       <br><b>Caveat for that owner:</b> the roles passed there are ladder roles
 *       ({@code RBIO_DEALING_OFFICIAL}, {@code RBIO_REVIEWER}, {@code RBIO_CONCILIATOR},
 *       {@code RBIO_ADJUDICATOR}, {@code RBIO_DEPUTY_OMBUDSMAN}, {@code RBIO_OMBUDSMAN}), and
 *       {@code WF_OFFICER_POOL} holds rows for none of them — only {@code RBIO_OFFICER} and
 *       {@code RBIO_SUPERVISOR}. Rotation still works (Keycloak supplies the candidates and a missing
 *       pool row means "available"), but leave and deactivation cannot be recorded for those roles
 *       until pool rows exist. Seed them in that session's own migration.</li>
 *
 *   <li><b>{@code EmailSyndicationApiController.assignToNextDeo()}</b> — replace the static
 *       {@code AtomicInteger} with {@code assignNext("CRPC_DEO")}. Two behaviours there should change
 *       at the same time: it returns the literal {@code "Unassigned"} on an empty pool (the same
 *       fabrication class as {@code "RBIO OFFICER Team"}), and {@code getDeoPool()} falls back to a
 *       hardcoded {@code deo_001/002/003} list when Keycloak is silent, which assigns work to accounts
 *       that may not exist. Its {@code POST /deo/reset-pointer} endpoint should call
 *       {@link #resetPointer(String)}, which clears the pointer for every pod rather than only the one
 *       that served the request.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DurableRoundRobinAssigner {

    private final AaAssignmentCounterRepository counterRepository;
    private final AaAssignmentCounterInitialiser counterInitialiser;
    private final AaOfficerPoolRepository officerPoolRepository;
    private final KeycloakUserService keycloakUserService;

    /**
     * The outcome of an assignment attempt.
     *
     * <p>A distinct type rather than a bare nullable String so that "nobody is available" is a value
     * the caller must look at, not an absence it can accidentally treat as an officer. The previous
     * code returned a fabricated {@code "RBIO OFFICER Team"} string on an empty pool and wrote it into
     * {@code Complaint.assignedOfficer}, so a complaint appeared assigned to a person who does not
     * exist and no dashboard could tell the difference.
     */
    public record Assignment(String officerId, String reason) {

        public boolean isAssigned() {
            return officerId != null && !officerId.isBlank();
        }

        static Assignment to(String officerId) {
            return new Assignment(officerId, null);
        }

        static Assignment unassigned(String reason) {
            return new Assignment(null, reason);
        }
    }

    /**
     * Picks the next officer for {@code roleGroup}, advancing the durable pointer.
     *
     * <p>Assignment failure is a normal outcome, never an exception: a complaint must still be
     * registered when no officer can take it, so that it is visible and can be picked up or escalated.
     * Losing a citizen's complaint because a rota was empty would be far worse than leaving it
     * unassigned.
     *
     * @param roleGroup the Keycloak role / pool role_group to rotate within (e.g. {@code RBIO_OFFICER})
     * @return the chosen officer, or an unassigned result carrying the reason
     */
    @Transactional
    public Assignment assignNext(String roleGroup) {
        if (roleGroup == null || roleGroup.isBlank()) {
            return Assignment.unassigned("No role group supplied");
        }

        // MUST come before the lock — see the class javadoc on the gap-lock self-deadlock.
        counterInitialiser.ensureExists(roleGroup);

        // MUST come before reading candidates, so the candidate snapshot is inside the critical section.
        AaAssignmentCounter counter = counterRepository.findByRoleGroupForUpdate(roleGroup).orElse(null);
        if (counter == null) {
            // ensureExists committed a row, so this means the row was removed between the two calls.
            // Refuse rather than assign without serialisation.
            log.error("Assignment pointer for {} vanished after initialisation — refusing to assign", roleGroup);
            return Assignment.unassigned("Assignment pointer unavailable for " + roleGroup);
        }

        List<String> candidates = eligibleCandidates(roleGroup);
        if (candidates.isEmpty()) {
            log.warn("No eligible officer in role group {} — complaint will be left unassigned", roleGroup);
            return Assignment.unassigned("No active, available officer in " + roleGroup);
        }

        String chosen = nextAfter(candidates, counter.getLastAssignedUserId());

        counter.setLastAssignedUserId(chosen);
        counter.setUpdatedAt(LocalDateTime.now());
        counterRepository.save(counter);

        log.debug("Assigned {} from role group {} (previous holder {})",
                chosen, roleGroup, counter.getLastAssignedUserId());
        return Assignment.to(chosen);
    }

    /**
     * Keycloak members of the role, minus anyone the pool marks inactive or on leave.
     *
     * <p>Sorted so the rotation has a stable, reproducible base order. Without a total order the
     * pointer would be meaningless: "the next id after X" requires the list to be ordered the same way
     * on every pod and every call.
     */
    private List<String> eligibleCandidates(String roleGroup) {
        List<Map<String, Object>> members;
        try {
            members = keycloakUserService.getUsersByRole(roleGroup);
        } catch (Exception e) {
            // Fail closed: an unreachable Keycloak is not evidence that nobody is available, so the
            // complaint is left unassigned rather than assigned to a guess.
            log.error("Could not read role members for {} from Keycloak: {}", roleGroup, e.getMessage());
            return List.of();
        }
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        Set<String> unavailable = unavailableInPool(roleGroup);

        List<String> candidates = new ArrayList<>();
        for (Map<String, Object> member : members) {
            Object userId = member.get("userId");
            if (userId == null) continue;
            String id = String.valueOf(userId);
            if (id.isBlank() || unavailable.contains(id)) continue;
            candidates.add(id);
        }
        Collections.sort(candidates);
        return candidates;
    }

    /** User ids the pool explicitly marks as unavailable. Absence of a row means available. */
    private Set<String> unavailableInPool(String roleGroup) {
        Set<String> unavailable = new HashSet<>();
        try {
            for (AaOfficerPool row : officerPoolRepository.findByRoleGroupOrderByUserIdAsc(roleGroup)) {
                if (!row.isActive() || row.isOnLeave()) {
                    unavailable.add(row.getUserId());
                }
            }
        } catch (Exception e) {
            // A pool read failure must not silently widen eligibility to include officers who are on
            // leave, so this is logged loudly. It does not fail the assignment, because the pool is an
            // exclusion list rather than the candidate source.
            log.error("Could not read officer pool for {} — leave/active status NOT applied: {}",
                    roleGroup, e.getMessage());
        }
        return unavailable;
    }

    /**
     * The first candidate ordered after {@code lastAssigned}, wrapping to the head.
     *
     * <p>{@code lastAssigned} is only compared, never looked up, so a departed officer still defines a
     * valid position and rotation resumes after them instead of restarting.
     */
    private String nextAfter(List<String> orderedCandidates, String lastAssigned) {
        if (lastAssigned == null || lastAssigned.isBlank()) {
            return orderedCandidates.get(0);
        }
        for (String candidate : orderedCandidates) {
            if (candidate.compareTo(lastAssigned) > 0) {
                return candidate;
            }
        }
        return orderedCandidates.get(0);
    }

    /**
     * Clears the rotation pointer so the next assignment starts at the head of the list.
     *
     * <p>Exists so that {@code EmailSyndicationApiController}'s {@code POST /deo/reset-pointer} has a
     * durable equivalent to adopt: today it resets an in-memory {@code AtomicInteger}, which resets
     * only its own pod.
     */
    @Transactional
    public void resetPointer(String roleGroup) {
        counterInitialiser.ensureExists(roleGroup);
        counterRepository.findByRoleGroupForUpdate(roleGroup).ifPresent(counter -> {
            counter.setLastAssignedUserId(null);
            counter.setUpdatedAt(LocalDateTime.now());
            counterRepository.save(counter);
            log.info("Rotation pointer for {} reset to the head of the list", roleGroup);
        });
    }

    /** The officer who most recently received work in this role group, for diagnostics and tests. */
    @Transactional(readOnly = true)
    public Optional<String> currentPointer(String roleGroup) {
        return counterRepository.findByRoleGroup(roleGroup)
                .map(AaAssignmentCounter::getLastAssignedUserId);
    }
}
