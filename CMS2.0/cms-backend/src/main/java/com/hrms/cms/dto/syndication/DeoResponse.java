package com.hrms.cms.dto.syndication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A data entry operator in the assignment pool. Keycloak owns the roster; OFFICER_AVAILABILITY owns
 * leave state and the per-officer threshold, so a DEO with no availability row still appears here on
 * the default threshold rather than dropping out of the pool.
 *
 * <p>{@link #currentLoad} and {@link #currentAssignedCount} are the same number under two names,
 * because the assignment screens and the team-management screen each grew their own.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeoResponse {

    private Integer id;
    private String userId;
    private String displayName;
    private String email;
    private Boolean isActive;
    private Boolean isOnLeave;
    private String leaveReason;
    private String officeCode;
    private Integer maxThreshold;
    private Integer currentLoad;
    private Integer currentAssignedCount;
    private Integer sortOrder;
}
