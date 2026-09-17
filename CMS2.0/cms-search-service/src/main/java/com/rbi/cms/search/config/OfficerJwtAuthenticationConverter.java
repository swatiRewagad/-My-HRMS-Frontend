package com.rbi.cms.search.config;

import com.rbi.cms.search.dto.OfficerPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the officer principal from token claims alone.
 *
 * <p>No I/O here by design. This runs on every authenticated request, so a network call would put a
 * remote dependency in front of every search — and, more importantly, an authorization attribute
 * fetched at request time has to be cached to be affordable, and a cached authorization attribute is a
 * window in which a transferred officer keeps reading their former department's complaints.
 *
 * <p>Realm-role extraction duplicates {@code cms-api-gateway}'s {@code KeycloakJwtAuthConverter} (and
 * two further copies in {@code cms-infrastructure} and {@code cms-mail-intake}). It is not promoted to
 * {@code cms-common} because that module has no Spring Security dependency, and adding one would push
 * security onto all of its consumers.
 */
@Slf4j
@Component
public class OfficerJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_USERNAME = "preferred_username";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_DEPARTMENT = "department";

    private final JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> roles = extractRealmRoles(jwt);

        Collection<GrantedAuthority> authorities = Stream.concat(
                scopeAuthoritiesConverter.convert(jwt).stream(),
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        ).collect(Collectors.toSet());

        String userName = jwt.getClaimAsString(CLAIM_USERNAME);
        String displayName = jwt.getClaimAsString(CLAIM_NAME);

        OfficerPrincipal officer = OfficerPrincipal.builder()
                .userName(userName)
                .subject(jwt.getSubject())
                .displayName(displayName != null && !displayName.isBlank() ? displayName : userName)
                .roles(roles)
                .department(extractDepartment(jwt))
                .build();

        return new OfficerAuthenticationToken(jwt, authorities, officer);
    }

    /**
     * Reads the department claim, tolerating the single-element array a multivalued mapper produces.
     *
     * <p>The tolerance is worth the few lines: leaving the mapper's "Multivalued" toggle on is an easy
     * misconfiguration, and the symptom would otherwise be every officer getting a 403 with nothing in
     * the logs pointing at the realm.
     *
     * <p>Returns null when absent. Deliberately not defaulted — an unscoped principal would read the
     * whole corpus.
     */
    private String extractDepartment(Jwt jwt) {
        Object raw = jwt.getClaim(CLAIM_DEPARTMENT);

        if (raw instanceof String text) {
            return text.isBlank() ? null : text;
        }
        if (raw instanceof Collection<?> values) {
            if (values.size() > 1) {
                log.warn("Token for '{}' carries {} department values; the mapper should be single-valued. "
                                + "Refusing to guess which one applies.",
                        jwt.getClaimAsString(CLAIM_USERNAME), values.size());
                return null;
            }
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        if (raw != null) {
            log.warn("Unexpected department claim type {} for '{}'",
                    raw.getClass().getSimpleName(), jwt.getClaimAsString(CLAIM_USERNAME));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
        if (realmAccess == null) {
            return List.of();
        }

        Object roles = realmAccess.get(CLAIM_ROLES);
        if (!(roles instanceof Collection<?>)) {
            return List.of();
        }

        return ((Collection<Object>) roles).stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .toList();
    }
}
