package com.rbi.cms.assignment.dto.response;

import java.util.List;

public record ResolveResponse(
    OutcomeDto outcome,
    ExplanationDto explanation,
    List<TraceDto> trace
) {}
