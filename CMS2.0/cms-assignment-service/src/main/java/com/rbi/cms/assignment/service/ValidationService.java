package com.rbi.cms.assignment.service;

import com.rbi.cms.assignment.domain.entity.*;
import com.rbi.cms.assignment.domain.enums.OperatorCode;
import com.rbi.cms.assignment.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final RuleRepository ruleRepository;
    private final RuleConditionRepository conditionRepository;
    private final RuleOutcomeRepository outcomeRepository;

    public ValidationReport validate(Long versionId) {
        List<AsgnRule> rules = ruleRepository.findByVersionIdOrderByPriorityAscRowOrderAsc(versionId);
        if (rules.isEmpty()) {
            return new ValidationReport(List.of(new ValidationIssue("ERROR", "NO_RULES", null, null,
                    "Version has no rules defined")), true);
        }

        List<Long> ruleIds = rules.stream().map(AsgnRule::getId).toList();
        Map<Long, List<AsgnRuleCondition>> condsByRule = conditionRepository.findByRuleIdIn(ruleIds)
                .stream().collect(Collectors.groupingBy(AsgnRuleCondition::getRuleId));
        Map<Long, AsgnRuleOutcome> outcomesByRule = outcomeRepository.findByRuleIdIn(ruleIds)
                .stream().collect(Collectors.toMap(AsgnRuleOutcome::getRuleId, o -> o, (a, b) -> a));

        List<ValidationIssue> issues = new ArrayList<>();

        // Check: rules without outcomes
        for (AsgnRule rule : rules) {
            if (!outcomesByRule.containsKey(rule.getId())) {
                issues.add(new ValidationIssue("ERROR", "MISSING_OUTCOME", rule.getRuleCode(), null,
                        "Rule '" + rule.getRuleCode() + "' has no outcome defined"));
            }
        }

        // Check: duplicate rule codes
        Map<String, Long> codeCounts = rules.stream()
                .collect(Collectors.groupingBy(AsgnRule::getRuleCode, Collectors.counting()));
        codeCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> issues.add(new ValidationIssue("ERROR", "DUPLICATE_CODE", e.getKey(), null,
                        "Rule code '" + e.getKey() + "' appears " + e.getValue() + " times")));

        // Check: numeric range overlaps per attribute
        detectNumericOverlaps(rules, condsByRule, issues);

        // Check: subsumption (unreachable rules — a rule whose conditions are a strict subset of a higher-priority rule)
        detectSubsumption(rules, condsByRule, issues);

        // Check: rules with no conditions (catch-all in non-last position)
        for (int i = 0; i < rules.size() - 1; i++) {
            AsgnRule rule = rules.get(i);
            List<AsgnRuleCondition> conds = condsByRule.getOrDefault(rule.getId(), List.of());
            if (conds.isEmpty()) {
                issues.add(new ValidationIssue("WARN", "CATCH_ALL_NOT_LAST", rule.getRuleCode(), null,
                        "Rule '" + rule.getRuleCode() + "' has no conditions but is not the last rule — rules below it are unreachable"));
            }
        }

        // Check: disabled rules
        long disabledCount = rules.stream().filter(r -> !r.isEnabled()).count();
        if (disabledCount > 0) {
            issues.add(new ValidationIssue("INFO", "DISABLED_RULES", null, null,
                    disabledCount + " rule(s) are disabled and will be skipped during evaluation"));
        }

        boolean valid = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));
        return new ValidationReport(issues, valid);
    }

    private void detectNumericOverlaps(List<AsgnRule> rules, Map<Long, List<AsgnRuleCondition>> condsByRule,
                                        List<ValidationIssue> issues) {
        // Group conditions by attribute code, only consider numeric range operators
        Map<String, List<RangeEntry>> rangesByAttr = new HashMap<>();
        for (AsgnRule rule : rules) {
            for (AsgnRuleCondition cond : condsByRule.getOrDefault(rule.getId(), List.of())) {
                if (isRangeOperator(cond.getOperator()) && cond.getValueNumFrom() != null) {
                    rangesByAttr.computeIfAbsent(cond.getAttributeCode(), k -> new ArrayList<>())
                            .add(new RangeEntry(rule.getRuleCode(), cond.getOperator(),
                                    cond.getValueNumFrom(), cond.getValueNumTo()));
                }
            }
        }

        for (var entry : rangesByAttr.entrySet()) {
            String attrCode = entry.getKey();
            List<RangeEntry> ranges = entry.getValue();
            for (int i = 0; i < ranges.size(); i++) {
                for (int j = i + 1; j < ranges.size(); j++) {
                    if (rangesOverlap(ranges.get(i), ranges.get(j))) {
                        issues.add(new ValidationIssue("WARN", "RANGE_OVERLAP", ranges.get(i).ruleCode(), attrCode,
                                "Numeric range overlap on '" + attrCode + "' between rules '" +
                                        ranges.get(i).ruleCode() + "' and '" + ranges.get(j).ruleCode() + "'"));
                    }
                }
            }
        }
    }

    private void detectSubsumption(List<AsgnRule> rules, Map<Long, List<AsgnRuleCondition>> condsByRule,
                                    List<ValidationIssue> issues) {
        for (int i = 0; i < rules.size(); i++) {
            Set<String> higherCondAttrs = condsByRule.getOrDefault(rules.get(i).getId(), List.of())
                    .stream().map(AsgnRuleCondition::getAttributeCode).collect(Collectors.toSet());
            if (higherCondAttrs.isEmpty()) continue;

            for (int j = i + 1; j < rules.size(); j++) {
                Set<String> lowerCondAttrs = condsByRule.getOrDefault(rules.get(j).getId(), List.of())
                        .stream().map(AsgnRuleCondition::getAttributeCode).collect(Collectors.toSet());

                if (higherCondAttrs.containsAll(lowerCondAttrs) && lowerCondAttrs.containsAll(higherCondAttrs)) {
                    // Same set of attributes — check if conditions are identical
                    if (conditionsIdentical(
                            condsByRule.getOrDefault(rules.get(i).getId(), List.of()),
                            condsByRule.getOrDefault(rules.get(j).getId(), List.of()))) {
                        issues.add(new ValidationIssue("WARN", "DUPLICATE_CONDITIONS",
                                rules.get(j).getRuleCode(), null,
                                "Rule '" + rules.get(j).getRuleCode() + "' has identical conditions to '" +
                                        rules.get(i).getRuleCode() + "' (higher priority) — may be unreachable"));
                    }
                }
            }
        }
    }

    private boolean conditionsIdentical(List<AsgnRuleCondition> a, List<AsgnRuleCondition> b) {
        if (a.size() != b.size()) return false;
        Map<String, AsgnRuleCondition> mapA = a.stream()
                .collect(Collectors.toMap(AsgnRuleCondition::getAttributeCode, c -> c));
        for (AsgnRuleCondition cb : b) {
            AsgnRuleCondition ca = mapA.get(cb.getAttributeCode());
            if (ca == null) return false;
            if (ca.getOperator() != cb.getOperator()) return false;
            if (!Objects.equals(ca.getValueText(), cb.getValueText())) return false;
            if (!Objects.equals(ca.getValueNumFrom(), cb.getValueNumFrom())) return false;
            if (!Objects.equals(ca.getValueNumTo(), cb.getValueNumTo())) return false;
            if (!Objects.equals(ca.getValueList(), cb.getValueList())) return false;
        }
        return true;
    }

    private boolean isRangeOperator(OperatorCode op) {
        return op == OperatorCode.GTE || op == OperatorCode.GT || op == OperatorCode.LTE ||
                op == OperatorCode.LT || op == OperatorCode.BETWEEN;
    }

    private boolean rangesOverlap(RangeEntry a, RangeEntry b) {
        BigDecimal aLo = a.from();
        BigDecimal aHi = a.to() != null ? a.to() : a.from();
        BigDecimal bLo = b.from();
        BigDecimal bHi = b.to() != null ? b.to() : b.from();

        if (a.operator() == OperatorCode.BETWEEN && b.operator() == OperatorCode.BETWEEN) {
            // [aLo, aHi) and [bLo, bHi) overlap if aLo < bHi && bLo < aHi
            return aLo.compareTo(bHi) < 0 && bLo.compareTo(aHi) < 0;
        }
        return false;
    }

    public record ValidationIssue(String severity, String code, String ruleCode, String attributeCode, String message) {}
    public record ValidationReport(List<ValidationIssue> issues, boolean valid) {}

    private record RangeEntry(String ruleCode, OperatorCode operator, BigDecimal from, BigDecimal to) {}
}
