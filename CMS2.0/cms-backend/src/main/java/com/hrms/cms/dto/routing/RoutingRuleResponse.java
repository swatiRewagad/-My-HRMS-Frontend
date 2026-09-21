package com.hrms.cms.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One human-readable row of the routing-rules reference table. Documentation for officers rather than
 * something the router reads — the rules themselves live in code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingRuleResponse {

    /** The intake channel the rule covers, e.g. {@code "Email (EMAIL)"}. */
    private String source;
    private String initialRoute;
    /** Prose description of the hops, e.g. {@code "DEO → Reviewer → RBIO or CEPC"}. */
    private String flow;
}
