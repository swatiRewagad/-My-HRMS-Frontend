package com.rbi.cms.assignment.engine.evaluator;

import com.rbi.cms.assignment.domain.enums.HitPolicy;
import com.rbi.cms.assignment.engine.compiler.CompiledCondition;
import com.rbi.cms.assignment.engine.compiler.CompiledRule;
import com.rbi.cms.assignment.engine.compiler.CompiledRuleSet;
import com.rbi.cms.assignment.engine.operator.ConditionOperator;
import com.rbi.cms.assignment.engine.operator.OperatorRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEvaluator {

    private final OperatorRegistry operatorRegistry;

    public EvaluationResult evaluate(CompiledRuleSet ruleSet, Map<String, Object> context, boolean fullTrace) {
        if (ruleSet == null) {
            return EvaluationResult.ofNoMatch(List.of());
        }

        List<TraceEntry> trace = fullTrace ? new ArrayList<>() : null;
        HitPolicy hitPolicy = ruleSet.hitPolicy();

        return switch (hitPolicy) {
            case FIRST -> evaluateFirst(ruleSet, context, trace);
            case PRIORITY_SPECIFICITY -> evaluatePrioritySpecificity(ruleSet, context, trace);
            case UNIQUE -> evaluateUnique(ruleSet, context, trace);
            case COLLECT -> evaluateFirst(ruleSet, context, trace); // Phase 4 for full COLLECT
        };
    }

    private EvaluationResult evaluateFirst(CompiledRuleSet ruleSet, Map<String, Object> context, List<TraceEntry> trace) {
        for (CompiledRule rule : ruleSet.rules()) {
            MatchResult match = matchRule(rule, context);
            if (trace != null) {
                trace.add(match.matched()
                        ? TraceEntry.matched(rule.ruleId(), rule.ruleCode(), rule.name())
                        : TraceEntry.failed(rule.ruleId(), rule.ruleCode(), rule.name(),
                                match.firstFailedAttribute(), match.failReason()));
            }
            if (match.matched()) {
                return EvaluationResult.ofMatch(rule, rule.outcome(), trace != null ? trace : List.of());
            }
        }
        return applyDefault(ruleSet, trace);
    }

    private EvaluationResult evaluatePrioritySpecificity(CompiledRuleSet ruleSet, Map<String, Object> context, List<TraceEntry> trace) {
        List<CompiledRule> matches = new ArrayList<>();

        for (CompiledRule rule : ruleSet.rules()) {
            MatchResult match = matchRule(rule, context);
            if (trace != null) {
                trace.add(match.matched()
                        ? TraceEntry.matched(rule.ruleId(), rule.ruleCode(), rule.name())
                        : TraceEntry.failed(rule.ruleId(), rule.ruleCode(), rule.name(),
                                match.firstFailedAttribute(), match.failReason()));
            }
            if (match.matched()) {
                matches.add(rule);
            }
        }

        if (matches.isEmpty()) {
            return applyDefault(ruleSet, trace);
        }

        // Pick: lowest priority, then highest specificity (fewest wildcards), then lowest rowOrder
        CompiledRule best = matches.stream()
                .min(Comparator.comparingInt(CompiledRule::priority)
                        .thenComparingInt(CompiledRule::wildcardCount)
                        .thenComparingInt(CompiledRule::rowOrder))
                .orElseThrow();

        return EvaluationResult.ofMatch(best, best.outcome(), trace != null ? trace : List.of());
    }

    private EvaluationResult evaluateUnique(CompiledRuleSet ruleSet, Map<String, Object> context, List<TraceEntry> trace) {
        List<CompiledRule> matches = new ArrayList<>();

        for (CompiledRule rule : ruleSet.rules()) {
            MatchResult match = matchRule(rule, context);
            if (trace != null) {
                trace.add(match.matched()
                        ? TraceEntry.matched(rule.ruleId(), rule.ruleCode(), rule.name())
                        : TraceEntry.failed(rule.ruleId(), rule.ruleCode(), rule.name(),
                                match.firstFailedAttribute(), match.failReason()));
            }
            if (match.matched()) {
                matches.add(rule);
            }
        }

        if (matches.isEmpty()) {
            return applyDefault(ruleSet, trace);
        }
        if (matches.size() > 1) {
            log.warn("RULE_CONFLICT: {} rules matched under UNIQUE hit policy for {}", matches.size(), ruleSet.decisionPoint());
            return EvaluationResult.ofDefault(ruleSet.defaultOutcome(), "RULE_CONFLICT", trace != null ? trace : List.of());
        }

        CompiledRule single = matches.get(0);
        return EvaluationResult.ofMatch(single, single.outcome(), trace != null ? trace : List.of());
    }

    private MatchResult matchRule(CompiledRule rule, Map<String, Object> context) {
        for (CompiledCondition cond : rule.conditions()) {
            Object ctxValue = resolveContextValue(context, cond.attributeCode());
            ConditionOperator operator = operatorRegistry.get(cond.operator());
            boolean result = operator.test(ctxValue, cond.value());
            if (!result) {
                return MatchResult.failed(cond.attributeCode(),
                        cond.operator() + " check failed (ctx=" + ctxValue + ")");
            }
        }
        return MatchResult.MATCHED;
    }

    private Object resolveContextValue(Map<String, Object> context, String attributeCode) {
        // Flat lookup first
        Object val = context.get(attributeCode);
        if (val != null) return val;

        // Nested path support: check nested maps
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                Object nestedVal = ((Map<?, ?>) nested).get(attributeCode);
                if (nestedVal != null) return nestedVal;
            }
        }
        return null;
    }

    private EvaluationResult applyDefault(CompiledRuleSet ruleSet, List<TraceEntry> trace) {
        if (ruleSet.defaultOutcome() != null) {
            return EvaluationResult.ofDefault(ruleSet.defaultOutcome(), "NO_RULE_MATCHED", trace != null ? trace : List.of());
        }
        return EvaluationResult.ofDefault(null, "NO_DEFAULT_CONFIGURED", trace != null ? trace : List.of());
    }

    private record MatchResult(boolean matched, String firstFailedAttribute, String failReason) {
        static final MatchResult MATCHED = new MatchResult(true, null, null);
        static MatchResult failed(String attr, String reason) {
            return new MatchResult(false, attr, reason);
        }
    }
}
