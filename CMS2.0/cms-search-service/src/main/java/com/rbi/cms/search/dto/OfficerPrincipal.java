package com.rbi.cms.search.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The authenticated officer, built entirely from signed JWT claims.
 *
 * <p>Immutable on purpose: {@code department} is the tenancy boundary every search is filtered on, so
 * nothing downstream of authentication may adjust it.
 */
@Getter
@Builder
public class OfficerPrincipal {

    /**
     * Keycloak {@code preferred_username}. This is the identity complaints are stored against —
     * {@code assignedOfficer} holds a username, not a UUID — so it is the only value that may be used
     * in a data filter.
     */
    private final String userName;

    /**
     * Keycloak {@code sub}. Stable across username changes, and therefore the right identifier for
     * audit, but it appears nowhere in the complaint corpus and must never be used as a filter.
     */
    private final String subject;

    private final String displayName;

    private final List<String> roles;

    /**
     * Sourced from a Keycloak protocol-mapper claim. Null when the claim is absent, which is left for
     * the scope policy to reject: defaulting it here would silently widen access.
     */
    private final String department;

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
