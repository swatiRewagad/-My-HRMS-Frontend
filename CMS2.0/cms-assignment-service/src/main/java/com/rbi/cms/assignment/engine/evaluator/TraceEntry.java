package com.rbi.cms.assignment.engine.evaluator;

public record TraceEntry(
    Long ruleId,
    String ruleCode,
    String ruleName,
    boolean matched,
    String firstFailedAttribute,
    String failReason
) {
    public static TraceEntry matched(Long ruleId, String code, String name) {
        return new TraceEntry(ruleId, code, name, true, null, null);
    }

    public static TraceEntry failed(Long ruleId, String code, String name, String failedAttr, String reason) {
        return new TraceEntry(ruleId, code, name, false, failedAttr, reason);
    }
}
