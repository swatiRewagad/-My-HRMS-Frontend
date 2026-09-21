package com.hrms.cms.dto.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code appellable} and {@code newIn2026} are flags only some clauses carry, so they stay boxed and
 * NON_NULL rather than defaulting to {@code false} — the clause list previously omitted the keys
 * entirely and callers distinguish absent from false.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClosureClauseResponse {

    private String code;
    private String label;
    private String category;
    private Boolean appellable;
    private Boolean newIn2026;
}
