package com.rbi.cms.assignment.service;

import com.rbi.cms.assignment.config.TenantContext;
import com.rbi.cms.assignment.domain.entity.*;
import com.rbi.cms.assignment.domain.enums.*;
import com.rbi.cms.assignment.dto.request.RuleBulkSaveRequest;
import com.rbi.cms.assignment.dto.request.RuleSetCreateRequest;
import com.rbi.cms.assignment.dto.request.VersionCreateRequest;
import com.rbi.cms.assignment.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleSetAuthoringService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleSetVersionRepository versionRepository;
    private final RuleRepository ruleRepository;
    private final RuleConditionRepository conditionRepository;
    private final RuleOutcomeRepository outcomeRepository;
    private final TenantContext tenantContext;

    public AsgnRuleSet createRuleSet(RuleSetCreateRequest request) {
        String tenant = tenantContext.getCurrentTenant();
        AsgnRuleSet ruleSet = AsgnRuleSet.builder()
                .tenantId(tenant)
                .decisionPoint(request.decisionPoint())
                .name(request.name())
                .description(request.description())
                .hitPolicy(request.hitPolicy() != null ? HitPolicy.valueOf(request.hitPolicy()) : HitPolicy.FIRST)
                .active(true)
                .build();
        return ruleSetRepository.save(ruleSet);
    }

    public List<AsgnRuleSet> listRuleSets() {
        return ruleSetRepository.findByTenantIdAndActive(tenantContext.getCurrentTenant(), true);
    }

    public AsgnRuleSetVersion createVersion(Long ruleSetId, VersionCreateRequest request) {
        String tenant = tenantContext.getCurrentTenant();
        Integer nextVersion = versionRepository.findTopByRuleSetIdOrderByVersionNoDesc(ruleSetId)
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);

        AsgnRuleSetVersion version = AsgnRuleSetVersion.builder()
                .tenantId(tenant)
                .ruleSetId(ruleSetId)
                .versionNo(nextVersion)
                .status(VersionStatus.DRAFT)
                .build();
        version = versionRepository.save(version);

        if (request != null && request.cloneFromVersion() != null) {
            cloneRulesFromVersion(ruleSetId, request.cloneFromVersion(), version.getId(), tenant);
        }

        return version;
    }

    public List<AsgnRuleSetVersion> listVersions(Long ruleSetId) {
        return versionRepository.findByRuleSetIdOrderByVersionNoDesc(ruleSetId);
    }

    public Map<String, Object> getVersionPayload(Long ruleSetId, Long versionId) {
        AsgnRuleSetVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found: " + versionId));
        AsgnRuleSet ruleSet = ruleSetRepository.findById(ruleSetId)
                .orElseThrow(() -> new NoSuchElementException("RuleSet not found: " + ruleSetId));

        List<AsgnRule> rules = ruleRepository.findByVersionIdOrderByPriorityAscRowOrderAsc(versionId);
        List<Long> ruleIds = rules.stream().map(AsgnRule::getId).toList();
        Map<Long, List<AsgnRuleCondition>> condsByRule = conditionRepository.findByRuleIdIn(ruleIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(AsgnRuleCondition::getRuleId));
        Map<Long, AsgnRuleOutcome> outcomesByRule = outcomeRepository.findByRuleIdIn(ruleIds)
                .stream().collect(java.util.stream.Collectors.toMap(AsgnRuleOutcome::getRuleId, o -> o));

        AsgnRuleOutcome defaultOutcome = outcomeRepository.findByVersionIdAndIsDefault(versionId, true).orElse(null);

        List<Map<String, Object>> rulesPayload = rules.stream().map(rule -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rule.getId());
            row.put("ruleCode", rule.getRuleCode());
            row.put("name", rule.getName());
            row.put("description", rule.getDescription());
            row.put("priority", rule.getPriority());
            row.put("rowOrder", rule.getRowOrder());
            row.put("enabled", rule.isEnabled());
            row.put("conditions", condsByRule.getOrDefault(rule.getId(), List.of()));
            row.put("outcome", outcomesByRule.get(rule.getId()));
            return row;
        }).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleSet", ruleSet);
        payload.put("version", version);
        payload.put("rules", rulesPayload);
        payload.put("defaultOutcome", defaultOutcome);
        payload.put("etag", version.getOptLock() != null ? version.getOptLock().toString() : "0");
        return payload;
    }

    @Transactional
    public AsgnRuleSetVersion bulkSaveRules(Long ruleSetId, Long versionId, RuleBulkSaveRequest request, String etag) {
        String tenant = tenantContext.getCurrentTenant();
        AsgnRuleSetVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));

        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify a non-DRAFT version (status=" + version.getStatus() + ")");
        }

        // Optimistic lock check
        if (etag != null && !etag.isBlank()) {
            long expected = Long.parseLong(etag);
            if (version.getOptLock() != null && !version.getOptLock().equals(expected)) {
                throw new OptimisticLockException("Version was modified by another user");
            }
        }

        // Delete existing rules for this version
        List<AsgnRule> existingRules = ruleRepository.findByVersionIdOrderByPriorityAscRowOrderAsc(versionId);
        List<Long> existingRuleIds = existingRules.stream().map(AsgnRule::getId).toList();
        if (!existingRuleIds.isEmpty()) {
            conditionRepository.deleteAll(conditionRepository.findByRuleIdIn(existingRuleIds));
            outcomeRepository.deleteAll(outcomeRepository.findByRuleIdIn(existingRuleIds));
            ruleRepository.deleteAll(existingRules);
        }
        // Delete existing default
        outcomeRepository.findByVersionIdAndIsDefault(versionId, true).ifPresent(outcomeRepository::delete);

        // Save new rules
        for (RuleBulkSaveRequest.RuleDto ruleDto : request.rules()) {
            AsgnRule rule = AsgnRule.builder()
                    .tenantId(tenant)
                    .versionId(versionId)
                    .ruleCode(ruleDto.ruleCode())
                    .name(ruleDto.name())
                    .description(ruleDto.description())
                    .priority(ruleDto.priority())
                    .rowOrder(ruleDto.rowOrder())
                    .enabled(ruleDto.enabled())
                    .build();
            rule = ruleRepository.save(rule);

            if (ruleDto.conditions() != null) {
                for (RuleBulkSaveRequest.ConditionDto condDto : ruleDto.conditions()) {
                    AsgnRuleCondition cond = AsgnRuleCondition.builder()
                            .tenantId(tenant)
                            .ruleId(rule.getId())
                            .attributeCode(condDto.attributeCode())
                            .operator(OperatorCode.valueOf(condDto.operator()))
                            .valueText(condDto.valueText())
                            .valueNumFrom(condDto.valueNumFrom() != null ? new BigDecimal(condDto.valueNumFrom()) : null)
                            .valueNumTo(condDto.valueNumTo() != null ? new BigDecimal(condDto.valueNumTo()) : null)
                            .valueDateFrom(condDto.valueDateFrom() != null ? LocalDate.parse(condDto.valueDateFrom()) : null)
                            .valueDateTo(condDto.valueDateTo() != null ? LocalDate.parse(condDto.valueDateTo()) : null)
                            .valueList(condDto.valueList())
                            .build();
                    conditionRepository.save(cond);
                }
            }

            if (ruleDto.outcome() != null) {
                AsgnRuleOutcome outcome = AsgnRuleOutcome.builder()
                        .tenantId(tenant)
                        .ruleId(rule.getId())
                        .versionId(versionId)
                        .isDefault(false)
                        .outcomeType(OutcomeType.valueOf(ruleDto.outcome().outcomeType()))
                        .targetId(ruleDto.outcome().targetId())
                        .assignMode(ruleDto.outcome().assignMode() != null ? AssignMode.valueOf(ruleDto.outcome().assignMode()) : null)
                        .distributionStrategy(ruleDto.outcome().distributionStrategy() != null ? DistributionStrategy.valueOf(ruleDto.outcome().distributionStrategy()) : null)
                        .chainOrder(ruleDto.outcome().chainOrder())
                        .orgUnitFromAttribute(ruleDto.outcome().orgUnitFromAttribute())
                        .build();
                outcomeRepository.save(outcome);
            }
        }

        // Save default outcome
        if (request.defaultOutcome() != null) {
            AsgnRuleOutcome defOutcome = AsgnRuleOutcome.builder()
                    .tenantId(tenant)
                    .versionId(versionId)
                    .isDefault(true)
                    .outcomeType(OutcomeType.valueOf(request.defaultOutcome().outcomeType()))
                    .targetId(request.defaultOutcome().targetId())
                    .assignMode(request.defaultOutcome().assignMode() != null ? AssignMode.valueOf(request.defaultOutcome().assignMode()) : null)
                    .distributionStrategy(request.defaultOutcome().distributionStrategy() != null ? DistributionStrategy.valueOf(request.defaultOutcome().distributionStrategy()) : null)
                    .build();
            outcomeRepository.save(defOutcome);
        }

        version.setUpdatedAt(Instant.now());
        return versionRepository.save(version);
    }

    @Transactional
    public void deleteRule(Long ruleSetId, Long versionId, Long ruleId) {
        AsgnRuleSetVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));
        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify a non-DRAFT version");
        }

        conditionRepository.deleteAll(conditionRepository.findByRuleId(ruleId));
        outcomeRepository.findByRuleId(ruleId).ifPresent(outcomeRepository::delete);
        ruleRepository.deleteById(ruleId);
    }

    @Transactional
    public void reorderRules(Long versionId, List<Long> ruleIdsInOrder) {
        AsgnRuleSetVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));
        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify a non-DRAFT version");
        }

        for (int i = 0; i < ruleIdsInOrder.size(); i++) {
            AsgnRule rule = ruleRepository.findById(ruleIdsInOrder.get(i)).orElse(null);
            if (rule != null) {
                rule.setRowOrder((i + 1) * 10);
                ruleRepository.save(rule);
            }
        }
    }

    private void cloneRulesFromVersion(Long ruleSetId, Integer sourceVersionNo, Long targetVersionId, String tenant) {
        AsgnRuleSetVersion source = versionRepository.findByRuleSetIdAndVersionNo(ruleSetId, sourceVersionNo)
                .orElseThrow(() -> new NoSuchElementException("Source version not found: " + sourceVersionNo));

        List<AsgnRule> sourceRules = ruleRepository.findByVersionIdOrderByPriorityAscRowOrderAsc(source.getId());
        for (AsgnRule sr : sourceRules) {
            AsgnRule newRule = AsgnRule.builder()
                    .tenantId(tenant)
                    .versionId(targetVersionId)
                    .ruleCode(sr.getRuleCode())
                    .name(sr.getName())
                    .description(sr.getDescription())
                    .priority(sr.getPriority())
                    .rowOrder(sr.getRowOrder())
                    .enabled(sr.isEnabled())
                    .build();
            AsgnRule savedRule = ruleRepository.save(newRule);

            List<AsgnRuleCondition> sourceConds = conditionRepository.findByRuleId(sr.getId());
            for (AsgnRuleCondition sc : sourceConds) {
                AsgnRuleCondition newCond = AsgnRuleCondition.builder()
                        .tenantId(tenant)
                        .ruleId(savedRule.getId())
                        .attributeCode(sc.getAttributeCode())
                        .operator(sc.getOperator())
                        .valueText(sc.getValueText())
                        .valueNumFrom(sc.getValueNumFrom())
                        .valueNumTo(sc.getValueNumTo())
                        .valueDateFrom(sc.getValueDateFrom())
                        .valueDateTo(sc.getValueDateTo())
                        .valueList(sc.getValueList())
                        .build();
                conditionRepository.save(newCond);
            }

            outcomeRepository.findByRuleId(sr.getId()).ifPresent(so -> {
                AsgnRuleOutcome newOut = AsgnRuleOutcome.builder()
                        .tenantId(tenant)
                        .ruleId(savedRule.getId())
                        .versionId(targetVersionId)
                        .isDefault(false)
                        .outcomeType(so.getOutcomeType())
                        .targetId(so.getTargetId())
                        .assignMode(so.getAssignMode())
                        .distributionStrategy(so.getDistributionStrategy())
                        .chainOrder(so.getChainOrder())
                        .orgUnitFromAttribute(so.getOrgUnitFromAttribute())
                        .build();
                outcomeRepository.save(newOut);
            });
        }

        // Clone default
        outcomeRepository.findByVersionIdAndIsDefault(source.getId(), true).ifPresent(so -> {
            AsgnRuleOutcome defOut = AsgnRuleOutcome.builder()
                    .tenantId(tenant)
                    .versionId(targetVersionId)
                    .isDefault(true)
                    .outcomeType(so.getOutcomeType())
                    .targetId(so.getTargetId())
                    .assignMode(so.getAssignMode())
                    .distributionStrategy(so.getDistributionStrategy())
                    .build();
            outcomeRepository.save(defOut);
        });
    }

    public static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String msg) { super(msg); }
    }
}
