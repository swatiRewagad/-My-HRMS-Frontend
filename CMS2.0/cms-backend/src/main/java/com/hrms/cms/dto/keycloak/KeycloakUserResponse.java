package com.hrms.cms.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A realm user as this system sees one, flattened from Keycloak's representation.
 *
 * <p>Note that Keycloak's raw {@code attributes} map is <em>not</em> carried through — the only
 * attribute this system reads is {@code officeCode}, which is lifted to a field here. Anything reaching
 * for another attribute off one of these objects is reading something that was never sent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakUserResponse {

    /** Keycloak's <em>username</em>, not its UUID — this is the id complaints are routed by. */
    private String userId;
    /** Keycloak's internal UUID. Needed only for admin API calls back into Keycloak. */
    private String id;
    /** First and last name joined, falling back to the username when neither is set. */
    private String displayName;
    /** Empty string rather than null when Keycloak holds no address. */
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;

    /**
     * Which RBI office the user is seated at. Absent rather than null when the Keycloak attribute is
     * missing, which is how it behaved as a Map and what the office filters expect.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String officeCode;
}
