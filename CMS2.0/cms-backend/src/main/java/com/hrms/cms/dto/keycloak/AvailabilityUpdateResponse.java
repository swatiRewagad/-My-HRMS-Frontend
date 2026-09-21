package com.hrms.cms.dto.keycloak;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The saved state of an officer's availability, echoed back after an update.
 *
 * <p>The Map this replaced spelled the two flags {@code active}/{@code onLeave} while the availability
 * <em>read</em> endpoint spelled the same two concepts {@code isActive}/{@code isOnLeave}. One concept
 * now has one name across the controller; the {@code active}/{@code onLeave} spelling survives only in
 * the <em>request</em> body, which is a separate contract and unchanged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityUpdateResponse {

    private String userId;
    /** Availability is per role, so the same person can be on leave as one role and not another. */
    private String role;
    private Boolean isActive;
    private Boolean isOnLeave;
    /** Derived from the three inputs — see {@code OfficerAvailabilityResponse.available}. */
    private Boolean available;
    private Integer currentWorkload;
    private Integer maxWorkload;
    private String officeCode;
}
