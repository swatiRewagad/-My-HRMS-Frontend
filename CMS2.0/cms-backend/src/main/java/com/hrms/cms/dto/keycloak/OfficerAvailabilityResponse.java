package com.hrms.cms.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * An officer plus whether they can currently be assigned work.
 *
 * <p>{@code @JsonInclude(NON_NULL)} at class level because the leave fields were genuinely
 * conditional: an officer with no availability row on file got defaults with the three leave keys
 * omitted entirely, and callers already treat absent and null alike.
 *
 * <p>The {@code Boolean} wrappers on the {@code isXxx} fields are load-bearing: Lombok would name a
 * primitive {@code boolean isActive} accessor {@code isActive()}, which Jackson publishes as
 * {@code "active"} — silently renaming the key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OfficerAvailabilityResponse {

    private String userId;
    private String id;
    private String displayName;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;

    /** Whether the officer is enabled for assignment at all. Defaults to true with no row on file. */
    private Boolean isActive;
    private Boolean isOnLeave;

    private LocalDate leaveStartDate;
    private LocalDate leaveEndDate;
    private String leaveReason;

    private Integer currentWorkload;
    private Integer maxWorkload;
    /** From the availability row, which is what Team Management edits — not from Keycloak. */
    private String officeCode;

    /**
     * Derived: active, not on leave, and under the workload ceiling. The one field the assignment
     * logic should consult rather than re-deriving from the three inputs.
     */
    private Boolean available;
}
