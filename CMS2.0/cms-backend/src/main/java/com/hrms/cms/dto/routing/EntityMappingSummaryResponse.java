package com.hrms.cms.dto.routing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How many regulated entities each department owns, plus where the lists came from.
 *
 * <p>The {@code @JsonProperty} names keep the original snake/upper-case keys: Lombok would name the
 * accessor for a field called {@code CEPC_count} something Jackson renders inconsistently, so the wire
 * names are pinned here instead of being derived.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityMappingSummaryResponse {

    @JsonProperty("CEPC_count")
    private long cepcCount;

    @JsonProperty("RBIO_count")
    private long rbioCount;

    private long total;

    /** Which RBI-published lists the counts were seeded from. */
    private String source;
}
