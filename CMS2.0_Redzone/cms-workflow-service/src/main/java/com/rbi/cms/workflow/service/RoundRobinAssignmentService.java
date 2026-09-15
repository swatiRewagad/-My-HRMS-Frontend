package com.rbi.cms.workflow.service;

import com.rbi.cms.workflow.entity.AssignmentCounter;
import com.rbi.cms.workflow.entity.OfficerPool;
import com.rbi.cms.workflow.repository.AssignmentCounterRepository;
import com.rbi.cms.workflow.repository.OfficerPoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoundRobinAssignmentService {

    private final OfficerPoolRepository officerPoolRepository;
    private final AssignmentCounterRepository counterRepository;

    /**
     * Assigns next available officer using round-robin with workload awareness.
     *
     * Strategy:
     * 1. Fetch eligible officers (active, not on leave, under max workload)
     * 2. Sort by current workload (least-loaded first for fairness)
     * 3. Apply round-robin index within eligible pool
     * 4. If all at capacity, fall back to least-loaded officer
     * 5. Persist counter and increment workload
     *
     * Uses pessimistic locking on counter to prevent duplicate assignment under concurrency.
     */
    @Transactional
    public String assignNext(String roleGroup, String regionalOffice) {
        List<OfficerPool> eligible = getEligibleOfficers(roleGroup, regionalOffice);

        if (eligible.isEmpty()) {
            log.warn("[ASSIGN] No eligible officers for roleGroup={}, regionalOffice={}. Falling back to all active.",
                    roleGroup, regionalOffice);
            eligible = officerPoolRepository.findByRoleGroupAndActiveTrue(roleGroup);
        }

        if (eligible.isEmpty()) {
            log.error("[ASSIGN] No officers configured for roleGroup={}", roleGroup);
            return null;
        }

        eligible.sort(Comparator.comparingInt(OfficerPool::getCurrentWorkload));

        int nextIndex = getAndIncrementIndex(roleGroup, eligible.size());
        OfficerPool assigned = eligible.get(nextIndex);

        if (assigned.getMaxWorkload() > 0 && assigned.getCurrentWorkload() >= assigned.getMaxWorkload()) {
            Optional<OfficerPool> leastLoaded = eligible.stream()
                    .filter(o -> o.getMaxWorkload() == 0 || o.getCurrentWorkload() < o.getMaxWorkload())
                    .min(Comparator.comparingInt(OfficerPool::getCurrentWorkload));

            if (leastLoaded.isPresent()) {
                assigned = leastLoaded.get();
            } else {
                log.warn("[ASSIGN] All officers at max capacity for {}. Assigning to least-loaded.", roleGroup);
                assigned = eligible.get(0);
            }
        }

        officerPoolRepository.incrementWorkload(assigned.getUserId());

        log.info("[ASSIGN] {} → {} (workload: {}/{})",
                roleGroup, assigned.getUserId(), assigned.getCurrentWorkload() + 1, assigned.getMaxWorkload());

        return assigned.getUserId();
    }

    /**
     * Overload without regional office filter.
     */
    @Transactional
    public String assignNext(String roleGroup) {
        return assignNext(roleGroup, null);
    }

    /**
     * Release workload when complaint is resolved/closed/transferred.
     */
    @Transactional
    public void releaseAssignment(String userId) {
        officerPoolRepository.decrementWorkload(userId);
        log.info("[ASSIGN] Released workload for user: {}", userId);
    }

    private List<OfficerPool> getEligibleOfficers(String roleGroup, String regionalOffice) {
        if (regionalOffice != null && !regionalOffice.isBlank()) {
            List<OfficerPool> regional = officerPoolRepository
                    .findByRoleGroupAndActiveTrueAndOnLeaveFalseAndRegionalOffice(roleGroup, regionalOffice);
            if (!regional.isEmpty()) return regional;
        }
        return officerPoolRepository.findByRoleGroupAndActiveTrueAndOnLeaveFalse(roleGroup);
    }

    private int getAndIncrementIndex(String roleGroup, int poolSize) {
        AssignmentCounter counter = counterRepository.findByRoleGroupForUpdate(roleGroup)
                .orElseGet(() -> counterRepository.save(AssignmentCounter.builder()
                        .roleGroup(roleGroup)
                        .lastAssignedIndex(0)
                        .build()));

        int nextIndex = (counter.getLastAssignedIndex() + 1) % poolSize;
        counter.setLastAssignedIndex(nextIndex);
        counterRepository.save(counter);

        return nextIndex;
    }
}
