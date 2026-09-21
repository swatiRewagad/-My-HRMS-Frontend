package com.hrms.cms.dto.keycloak;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The officer a round-robin pick landed on.
 *
 * <p>{@link #userId} and {@link #username} are deliberately the same value: Keycloak's username is the
 * id this system routes by, and both keys were on the wire, so both are kept rather than silently
 * dropping one that a caller might be reading.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextAssigneeResponse {

    private String userId;
    private String username;
    private String displayName;
    /** Empty string rather than null when the officer has no office attribute in Keycloak. */
    private String officeCode;
    /** Always {@code ROUND_ROBIN} today — named so a future strategy can be told apart. */
    private String assignmentMethod;
    /** How many officers the pick chose between, after any office filter. */
    private int totalPoolSize;
}
