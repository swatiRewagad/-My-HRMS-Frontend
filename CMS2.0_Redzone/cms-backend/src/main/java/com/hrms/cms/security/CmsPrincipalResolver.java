package com.hrms.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves WHO is calling and WHAT they may do, from the SSO token alone.
 *
 * <h2>The two principal types</h2>
 * Both RBI staff and Regulated Entity users sign in through the same SSO, so a token alone does not say
 * which kind of user it belongs to — except that an RE user's token carries an {@code entity_code} claim
 * naming the entity they act for. That claim is therefore the discriminator, and it is also the SCOPE: an
 * RE user may only ever see complaints against their own entity.
 *
 * <h2>Why this replaces reading claims at each call site</h2>
 * {@code entity_code} was being decoded independently in {@code AaParentComplaintController} and
 * {@code RePortalController}, each with its own base64 handling and its own fallback behaviour. Two
 * copies of an authorisation decision is how they come to disagree — and the disagreement is invisible
 * until one of them admits a caller the other would refuse.
 *
 * <h2>Fails closed, everywhere</h2>
 * Every path that cannot answer a question returns "no authority" or "no entity", never a permissive
 * default. An unreadable token must not produce an unscoped RE user, because that user would see every
 * entity's complaints — the exact leak the scope exists to prevent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmsPrincipalResolver {

    /** Where the SSO publishes the granted authorities. Kept configurable because the claim name is an
     *  integration detail owned by the SSO, not by this application. */
    @Value("${cms.security.authorities-claim:authorities}")
    private String authoritiesClaim;

    /** Honoured ONLY under dev-local, where E2E tests have no real token. */
    @Value("${cms.security.allow-dev-identity-headers:false}")
    private boolean allowDevIdentityHeaders;

    private final ObjectMapper objectMapper;

    /** What kind of user is calling. */
    public enum PrincipalType { RBI, REGULATED_ENTITY, UNKNOWN }

    /**
     * The caller, as far as the token can establish it.
     *
     * @param username     {@code preferred_username}, or null when unresolvable
     * @param entityCode   the RE this caller acts for, or null for RBI staff
     * @param authorities  the authorities the SSO granted, never null
     */
    public record Principal(String username, String entityCode, Set<String> authorities, PrincipalType type) {

        public boolean has(CmsAuthority authority) {
            return authorities.contains(authority.authority());
        }

        public boolean isRegulatedEntity() {
            return type == PrincipalType.REGULATED_ENTITY;
        }

        /** True when nothing about this caller could be established. */
        public boolean isAnonymous() {
            return type == PrincipalType.UNKNOWN && authorities.isEmpty();
        }
    }

    private static final Principal ANONYMOUS =
            new Principal(null, null, Set.of(), PrincipalType.UNKNOWN);

    public Principal resolve() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return ANONYMOUS;
        }
        HttpServletRequest request = attrs.getRequest();

        Map<String, Object> claims = decodeClaims(request.getHeader("Authorization"));

        String username = claims != null ? asString(claims.get("preferred_username")) : null;
        if (username == null && claims != null) {
            username = asString(claims.get("sub"));
        }
        String entityCode = claims != null ? asString(claims.get("entity_code")) : null;
        Set<String> authorities = claims != null ? extractAuthorities(claims) : new LinkedHashSet<>();

        // Dev headers are a LAST resort and only under dev-local. They are consulted after the token so a
        // real token can never be overridden by a header — that ordering was itself a fixed defect.
        if (allowDevIdentityHeaders) {
            if (username == null) username = request.getHeader("X-User-Id");
            if (entityCode == null) entityCode = firstNonBlank(
                    request.getHeader("X-Entity-Code"), request.getHeader("X-User-Entity"));
            if (authorities.isEmpty()) {
                // Validated against the catalogue exactly as the token path is. Adding the raw strings here
                // let an undeclared authority through on this path only, so a name this build cannot
                // enforce would still be reported as held — which is how a stale SSO mapping comes to look
                // correct. Found by its own test.
                for (String part : splitCsv(request.getHeader("X-User-Authorities"))) {
                    CmsAuthority.from(part).ifPresent(a -> authorities.add(a.authority()));
                }
            }
        }

        if (username == null && entityCode == null && authorities.isEmpty()) {
            return ANONYMOUS;
        }

        // An entity_code makes the caller an RE user. Absent it they are treated as RBI staff — but only
        // when something else identified them, so an empty token does not become "RBI staff".
        PrincipalType type = (entityCode != null && !entityCode.isBlank())
                ? PrincipalType.REGULATED_ENTITY
                : (username != null ? PrincipalType.RBI : PrincipalType.UNKNOWN);

        return new Principal(username, blankToNull(entityCode), Collections.unmodifiableSet(authorities), type);
    }

    /**
     * Reads the authorities claim, accepting either a list or a space/comma-separated string.
     *
     * <p>Both shapes are accepted because which one an identity provider emits is its choice, and being
     * strict here would mean the application refuses every caller after an SSO configuration change that
     * is otherwise correct. Unknown names are DROPPED rather than kept: an authority this build does not
     * declare cannot be enforced, and carrying it forward would let a stale SSO mapping appear to work.
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractAuthorities(Map<String, Object> claims) {
        Object raw = claims.get(authoritiesClaim);

        // Keycloak commonly nests authorities under realm_access alongside roles.
        if (raw == null) {
            Object realmAccess = claims.get("realm_access");
            if (realmAccess instanceof Map<?, ?> map) {
                raw = map.get(authoritiesClaim);
            }
        }

        Set<String> out = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                CmsAuthority.from(asString(o)).ifPresent(a -> out.add(a.authority()));
            }
        } else if (raw instanceof String s) {
            for (String part : splitCsv(s)) {
                CmsAuthority.from(part).ifPresent(a -> out.add(a.authority()));
            }
        }
        return out;
    }

    /**
     * Decodes the JWT payload without verifying the signature.
     *
     * <p>Verification is the resource server's job and has already happened by the time a guarded method
     * runs — this only reads claims the container already validated. It returns null on ANY problem, so a
     * malformed token yields an anonymous principal rather than a partially populated one.
     */
    private Map<String, Object> decodeClaims(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String[] parts = authHeader.substring(7).split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(new String(payload, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            log.debug("Could not decode the bearer token payload: {}", e.getMessage());
            return null;
        }
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.trim().split("[,\\s]+"));
    }

    private static String asString(Object o) {
        return (o instanceof String s && !s.isBlank()) ? s.trim() : null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }
}
