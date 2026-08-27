package com.rbi.cms.assignment.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

public record ResolveRequest(
    @NotBlank String decisionPoint,
    String tenantId,
    String caseRef,
    Instant asOf,
    TraceLevel traceLevel,
    boolean dryRun,
    Map<String, Object> context
) {
    public enum TraceLevel { NONE, MATCHED, FULL }

    public TraceLevel effectiveTraceLevel() {
        return traceLevel != null ? traceLevel : TraceLevel.NONE;
    }
}
