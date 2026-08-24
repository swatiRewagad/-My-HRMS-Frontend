package com.rbi.cms.assignment.engine.evaluator;

import com.rbi.cms.assignment.engine.compiler.CompiledOutcome;
import com.rbi.cms.assignment.engine.compiler.CompiledRule;

import java.util.List;

public record EvaluationResult(
    boolean matched,
    CompiledRule matchedRule,
    CompiledOutcome outcome,
    boolean fallbackApplied,
    String fallbackReason,
    List<TraceEntry> trace
) {
    public static EvaluationResult ofMatch(CompiledRule rule, CompiledOutcome outcome, List<TraceEntry> trace) {
        return new EvaluationResult(true, rule, outcome, false, null, trace);
    }

    public static EvaluationResult ofDefault(CompiledOutcome defaultOutcome, String reason, List<TraceEntry> trace) {
        return new EvaluationResult(false, null, defaultOutcome, true, reason, trace);
    }

    public static EvaluationResult ofNoMatch(List<TraceEntry> trace) {
        return new EvaluationResult(false, null, null, true, "NO_PUBLISHED_RULESET", trace);
    }
}
