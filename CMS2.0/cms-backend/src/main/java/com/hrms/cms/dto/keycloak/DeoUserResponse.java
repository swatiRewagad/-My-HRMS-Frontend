package com.hrms.cms.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A data entry operator, with the workload fields the assignment screens expect.
 *
 * <p>{@link #maxThreshold} and {@link #currentAssignedCount} are hardcoded placeholders — per-DEO
 * capacity is not tracked yet. They keep these names rather than the {@code maxLoad}/{@code currentLoad}
 * pair used for reviewers because the email-syndication DEO endpoint publishes them under these names
 * and the complaint screens read them from there; renaming here would leave two DEO endpoints disagreeing.
 *
 * <p>The {@code Boolean} wrappers on {@link #isActive} and {@link #isOnLeave} are load-bearing rather
 * than about nullability: Lombok would name a primitive {@code boolean isActive} accessor
 * {@code isActive()}, which Jackson publishes as {@code "active"} — silently renaming the key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeoUserResponse {

    private String userId;
    private String id;
    private String displayName;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String officeCode;

    /** Mirrors Keycloak's {@link #enabled} — leave tracking for DEOs is not wired up. */
    private Boolean isActive;
    /** Always false; DEO leave is not tracked. */
    private Boolean isOnLeave;
    private Integer maxThreshold;
    private Integer currentAssignedCount;
    /** Position in the listing, assigned on read — not a stored preference. */
    private Integer sortOrder;
}
