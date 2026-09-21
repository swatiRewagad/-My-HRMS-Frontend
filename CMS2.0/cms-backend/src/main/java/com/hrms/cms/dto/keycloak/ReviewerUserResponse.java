package com.hrms.cms.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A CRPC reviewer. {@link #isActive} and {@link #isOnLeave} are the two fields that matter — the
 * reviewer pickers filter on them to decide who can be assigned.
 *
 * <p>{@link #maxLoad}, {@link #currentLoad} and {@link #region} are hardcoded placeholders; per-reviewer
 * capacity and region are not tracked yet. See {@link DeoUserResponse} for why the DEO equivalents of
 * the two load fields are spelled differently.
 *
 * <p>The {@code Boolean} wrappers on the {@code isXxx} fields are load-bearing: Lombok would name a
 * primitive {@code boolean isActive} accessor {@code isActive()}, which Jackson publishes as
 * {@code "active"} — silently renaming the key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewerUserResponse {

    private String userId;
    private String id;
    private String displayName;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String officeCode;

    /** Mirrors Keycloak's {@link #enabled} — reviewer leave is not wired up. */
    private Boolean isActive;
    /** Always false; reviewer leave is not tracked. */
    private Boolean isOnLeave;
    private Integer maxLoad;
    private Integer currentLoad;
    /** Always empty; reviewer region is not tracked. */
    private String region;
    /** Position in the listing, assigned on read — not a stored preference. */
    private Integer sortOrder;
}
