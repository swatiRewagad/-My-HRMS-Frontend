package com.hrms.cms.dto.keycloak;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One active RBI office, for the office pickers. Inactive offices are filtered out before this is
 * built, so there is no active flag to carry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficeResponse {

    private String officeCode;
    private String officeName;
    /** e.g. regional office vs central office — drives which roles can be seated there. */
    private String officeType;
}
