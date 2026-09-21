package com.hrms.cms.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Which department an entity code routes to.
 *
 * <p>The Map this replaced also carried a hardcoded English {@code note} explaining that entity-based
 * routing only applies to the EMAIL and PHYSICAL_LETTER channels. It was documentation shipped inside a
 * payload, no caller read it, and it could not be localised — so it is not reproduced here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResolutionResponse {

    private String entityCode;
    private String department;
    /** {@code CEPC_OFFICER} or {@code RBIO_OFFICER}, derived from {@link #department}. */
    private String assignedRole;
}
