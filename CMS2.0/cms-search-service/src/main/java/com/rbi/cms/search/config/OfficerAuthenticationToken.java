package com.rbi.cms.search.config;

import com.rbi.cms.search.dto.OfficerPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * Carries a typed {@link OfficerPrincipal} as the authentication principal so the controller never has
 * to cast, while keeping the underlying {@link Jwt} available for anything that needs a raw claim.
 */
public class OfficerAuthenticationToken extends JwtAuthenticationToken {

    private final OfficerPrincipal officer;

    public OfficerAuthenticationToken(Jwt jwt, Collection<? extends GrantedAuthority> authorities,
                                      OfficerPrincipal officer) {
        super(jwt, authorities, officer.getUserName());
        this.officer = officer;
    }

    @Override
    public Object getPrincipal() {
        return officer;
    }

    public OfficerPrincipal getOfficer() {
        return officer;
    }
}
