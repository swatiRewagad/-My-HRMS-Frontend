package com.rbi.cms.assignment.service;

import com.rbi.cms.assignment.domain.entity.AsgnExclusionRule;
import com.rbi.cms.assignment.persistence.repository.ExclusionRuleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExclusionService {

    private final ExclusionRuleRepository exclusionRuleRepository;
    private final ObjectMapper objectMapper;

    public List<String> getExcludedCandidates(String tenantId, List<String> candidates, Map<String, Object> caseContext) {
        List<AsgnExclusionRule> rules = exclusionRuleRepository.findByTenantIdAndActive(tenantId, true);
        if (rules.isEmpty()) return List.of();

        List<String> excluded = new ArrayList<>();

        for (AsgnExclusionRule rule : rules) {
            try {
                ExclusionCondition condition = parseCondition(rule.getConditionJson());
                if (condition == null) continue;

                for (String candidate : candidates) {
                    if (excluded.contains(candidate)) continue;
                    if (shouldExclude(condition, candidate, caseContext)) {
                        excluded.add(candidate);
                        log.debug("Excluded candidate '{}' by rule '{}' ({})",
                                candidate, rule.getId(), rule.getExclusionType());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate exclusion rule {}: {}", rule.getId(), e.getMessage());
            }
        }

        return excluded;
    }

    private boolean shouldExclude(ExclusionCondition condition, String candidateId, Map<String, Object> caseContext) {
        switch (condition.type()) {
            case "SAME_ENTITY":
                // Exclude if candidateId is linked to the regulated entity in the case
                String reId = caseContext != null ? (String) caseContext.get("regulatedEntityId") : null;
                if (reId == null) return false;
                return condition.excludedPairs().stream()
                        .anyMatch(pair -> pair.userId().equals(candidateId) && pair.entityId().equals(reId));

            case "USER_LIST":
                // Simple blocklist
                return condition.blockedUsers() != null && condition.blockedUsers().contains(candidateId);

            case "REGION_MISMATCH":
                // Exclude if candidate's region doesn't match case region
                // Placeholder — requires user profile lookup
                return false;

            default:
                return false;
        }
    }

    private ExclusionCondition parseCondition(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            String type = (String) raw.get("type");
            List<String> blockedUsers = raw.containsKey("blockedUsers")
                    ? objectMapper.convertValue(raw.get("blockedUsers"), new TypeReference<>() {})
                    : null;
            List<EntityPair> pairs = raw.containsKey("excludedPairs")
                    ? objectMapper.convertValue(raw.get("excludedPairs"), new TypeReference<>() {})
                    : List.of();
            return new ExclusionCondition(type, blockedUsers, pairs);
        } catch (Exception e) {
            log.warn("Invalid exclusion condition JSON: {}", e.getMessage());
            return null;
        }
    }

    public record ExclusionCondition(String type, List<String> blockedUsers, List<EntityPair> excludedPairs) {}
    public record EntityPair(String userId, String entityId) {}
}
