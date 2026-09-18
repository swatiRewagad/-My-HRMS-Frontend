package com.hrms.cms.security;

import com.hrms.cms.service.RbioRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The acting RBIO role and user id for the current request.
 *
 * <p>Delegates to {@link AaIdentityResolver}, which already implements the token-first resolution this
 * needs: the JWT is authoritative and the {@code X-User-*} headers are honoured only when
 * {@code cms.security.allow-dev-identity-headers} is true. Reimplementing that here would be a sixth
 * copy of a precedence rule that has already been got wrong five times in this codebase — and the copy
 * would be the one that drifts.
 *
 * <p>Role scoping on the list endpoint therefore cannot be steered by a header on a deployed instance: a
 * caller cannot name a role they do not hold in order to widen what they can see.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbioIdentityResolver {

    private static final List<String> RBIO_ROLES = Arrays.asList(RbioRoles.ALL);

    private final AaIdentityResolver delegate;

    /**
     * The caller's RBIO role, or null when they hold none.
     *
     * <p>Null rather than a default: defaulting to an officer role would grant an unroled caller an
     * officer's view of the national complaint table.
     *
     * <p>Ordered by {@link RbioRoles#ALL} rather than by whatever the token happens to list first, so a
     * user holding two RBIO roles resolves deterministically instead of varying between requests.
     */
    public String resolveRbioRole() {
        Set<String> held = delegate.resolveRoles();
        for (String candidate : RBIO_ROLES) {
            if (held.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** The acting user id, or null when it cannot be established from the token. */
    public String resolveActor() {
        return delegate.resolveActor();
    }

    public Set<String> resolveRoles() {
        return delegate.resolveRoles();
    }
}
