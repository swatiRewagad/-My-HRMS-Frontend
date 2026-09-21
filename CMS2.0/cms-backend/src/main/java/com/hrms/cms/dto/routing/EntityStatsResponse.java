package com.hrms.cms.dto.routing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Regulated-entity counts per department. The two department keys are upper-case on the wire because
 * they are the department codes themselves; {@code @JsonProperty} pins them so they do not depend on
 * how Jackson happens to decapitalise an all-caps accessor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityStatsResponse {

    @JsonProperty("CEPC")
    private long cepc;

    @JsonProperty("RBIO")
    private long rbio;

    private long total;
}
