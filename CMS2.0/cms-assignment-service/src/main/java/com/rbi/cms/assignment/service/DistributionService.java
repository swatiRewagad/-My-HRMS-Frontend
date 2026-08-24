package com.rbi.cms.assignment.service;

import com.rbi.cms.assignment.config.TenantContext;
import com.rbi.cms.assignment.domain.entity.AsgnRoundRobinCounter;
import com.rbi.cms.assignment.domain.enums.DistributionStrategy;
import com.rbi.cms.assignment.persistence.repository.RoundRobinCounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionService {

    private final RoundRobinCounterRepository counterRepository;
    private final ExclusionService exclusionService;
    private final TenantContext tenantContext;

    @Transactional
    public DistributionResult distribute(
            String groupId,
            DistributionStrategy strategy,
            List<String> candidates,
            Map<String, Object> caseContext) {

        if (candidates == null || candidates.isEmpty()) {
            return new DistributionResult(null, 0, 0, List.of(), "No candidates available");
        }

        String tenant = tenantContext.getCurrentTenant();

        // Apply exclusion rules
        List<String> excluded = exclusionService.getExcludedCandidates(tenant, candidates, caseContext);
        List<String> eligible = candidates.stream()
                .filter(c -> !excluded.contains(c))
                .toList();

        if (eligible.isEmpty()) {
            return new DistributionResult(null, candidates.size(), excluded.size(),
                    excluded, "All candidates excluded by conflict-of-interest rules");
        }

        String selected = switch (strategy) {
            case ROUND_ROBIN -> roundRobin(tenant, groupId, eligible);
            case LEAST_ACTIVE_CASES -> leastActive(eligible);
            case RANDOM -> randomPick(eligible);
            case CAPACITY_WEIGHTED -> capacityWeighted(eligible);
            case SKILL_MATCH, LANGUAGE_MATCH -> eligible.getFirst(); // Placeholder: requires profile data
        };

        log.debug("Distribution: strategy={}, group={}, selected={} from {} eligible ({} excluded)",
                strategy, groupId, selected, eligible.size(), excluded.size());

        return new DistributionResult(selected, candidates.size(), excluded.size(), excluded, null);
    }

    private String roundRobin(String tenant, String groupId, List<String> eligible) {
        String strategyKey = "RR";
        AsgnRoundRobinCounter counter = counterRepository
                .findByTenantIdAndGroupIdAndStrategyKey(tenant, groupId, strategyKey)
                .orElseGet(() -> {
                    AsgnRoundRobinCounter newCounter = AsgnRoundRobinCounter.builder()
                            .tenantId(tenant)
                            .groupId(groupId)
                            .strategyKey(strategyKey)
                            .lastIndex(0)
                            .build();
                    return counterRepository.save(newCounter);
                });

        int nextIndex = (counter.getLastIndex() + 1) % eligible.size();
        counter.setLastIndex(nextIndex);
        counterRepository.save(counter);

        return eligible.get(nextIndex);
    }

    private String leastActive(List<String> eligible) {
        // In a full implementation, this would query active case counts per user.
        // For now, use round-robin-like behavior — first in list.
        // TODO Phase 6: integrate with workload metrics from cms-workflow-service
        return eligible.getFirst();
    }

    private String randomPick(List<String> eligible) {
        int idx = ThreadLocalRandom.current().nextInt(eligible.size());
        return eligible.get(idx);
    }

    private String capacityWeighted(List<String> eligible) {
        // Placeholder: capacity-weighted requires user capacity data
        // TODO Phase 6: integrate with capacity profiles
        return randomPick(eligible);
    }

    public record DistributionResult(
            String selectedUserId,
            int candidatesConsidered,
            int candidatesExcluded,
            List<String> excludedUsers,
            String failureReason
    ) {
        public boolean success() {
            return selectedUserId != null;
        }
    }
}
