package com.rbi.cms.assignment.engine.compiler;

import com.rbi.cms.assignment.domain.entity.*;
import com.rbi.cms.assignment.domain.enums.DataType;
import com.rbi.cms.assignment.domain.enums.OperatorCode;
import com.rbi.cms.assignment.engine.operator.ConditionValue;
import com.rbi.cms.assignment.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSetCompiler {

    private final RuleRepository ruleRepository;
    private final RuleConditionRepository conditionRepository;
    private final RuleOutcomeRepository outcomeRepository;
    private final AttributeRepository attributeRepository;

    public CompiledRuleSet compile(AsgnRuleSet ruleSet, AsgnRuleSetVersion version) {
        log.info("Compiling ruleset {} version {}", ruleSet.getDecisionPoint(), version.getVersionNo());

        Map<String, AsgnAttribute> attrMap = attributeRepository
                .findByTenantIdAndActiveOrderByDisplayOrder(ruleSet.getTenantId(), true)
                .stream()
                .collect(Collectors.toMap(AsgnAttribute::getCode, a -> a));

        List<AsgnRule> rules = ruleRepository.findByVersionIdAndEnabled(version.getId(), true);

        List<Long> ruleIds = rules.stream().map(AsgnRule::getId).toList();
        Map<Long, List<AsgnRuleCondition>> conditionsByRule = conditionRepository.findByRuleIdIn(ruleIds)
                .stream()
                .collect(Collectors.groupingBy(AsgnRuleCondition::getRuleId));
        Map<Long, AsgnRuleOutcome> outcomesByRule = outcomeRepository.findByRuleIdIn(ruleIds)
                .stream()
                .collect(Collectors.toMap(AsgnRuleOutcome::getRuleId, o -> o));

        List<CompiledRule> compiledRules = rules.stream()
                .map(rule -> compileRule(rule, conditionsByRule.getOrDefault(rule.getId(), List.of()),
                        outcomesByRule.get(rule.getId()), attrMap))
                .sorted(Comparator.comparingInt(CompiledRule::priority).thenComparingInt(CompiledRule::rowOrder))
                .toList();

        CompiledOutcome defaultOutcome = compileDefaultOutcome(version.getId());

        log.info("Compiled {} rules for {} v{}", compiledRules.size(), ruleSet.getDecisionPoint(), version.getVersionNo());

        return new CompiledRuleSet(
                ruleSet.getId(),
                version.getId(),
                version.getVersionNo(),
                ruleSet.getDecisionPoint(),
                ruleSet.getTenantId(),
                ruleSet.getHitPolicy(),
                compiledRules,
                defaultOutcome
        );
    }

    private CompiledRule compileRule(AsgnRule rule, List<AsgnRuleCondition> conditions,
                                     AsgnRuleOutcome outcome, Map<String, AsgnAttribute> attrMap) {
        int totalAttributes = attrMap.size();
        List<CompiledCondition> compiled = conditions.stream()
                .map(c -> compileCondition(c, attrMap.get(c.getAttributeCode())))
                .toList();

        int wildcardCount = totalAttributes - compiled.size();

        CompiledOutcome compiledOutcome = outcome != null ? toCompiledOutcome(outcome) : null;

        return new CompiledRule(
                rule.getId(),
                rule.getRuleCode(),
                rule.getName(),
                rule.getPriority(),
                rule.getRowOrder(),
                compiled,
                compiledOutcome,
                wildcardCount
        );
    }

    private CompiledCondition compileCondition(AsgnRuleCondition cond, AsgnAttribute attr) {
        DataType dataType = attr != null ? attr.getDataType() : DataType.STRING;
        boolean caseSensitive = attr != null && attr.isCaseSensitive();

        ConditionValue value = buildConditionValue(cond, dataType);

        return new CompiledCondition(
                cond.getAttributeCode(),
                dataType,
                cond.getOperator(),
                value,
                caseSensitive
        );
    }

    private ConditionValue buildConditionValue(AsgnRuleCondition cond, DataType dataType) {
        List<String> listValues = null;
        if (cond.getValueList() != null && !cond.getValueList().isBlank()) {
            listValues = Arrays.stream(cond.getValueList().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        return new ConditionValue(
                cond.getValueText(),
                cond.getValueNumFrom(),
                cond.getValueNumTo(),
                cond.getValueDateFrom(),
                cond.getValueDateTo(),
                listValues
        );
    }

    private CompiledOutcome compileDefaultOutcome(Long versionId) {
        return outcomeRepository.findByVersionIdAndIsDefault(versionId, true)
                .map(this::toCompiledOutcome)
                .orElse(null);
    }

    private CompiledOutcome toCompiledOutcome(AsgnRuleOutcome o) {
        return new CompiledOutcome(
                o.getOutcomeType(),
                o.getTargetId(),
                o.getAssignMode(),
                o.getDistributionStrategy(),
                o.getChainOrder(),
                o.getOrgUnitFromAttribute()
        );
    }
}
