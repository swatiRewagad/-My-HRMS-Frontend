package com.hrms.cms.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * The acting CEPC role and user id for the current request.
 *
 * <p>Delegates to {@link AaIdentityResolver} for exactly the reason {@link RbioIdentityResolver} does:
 * that class already implements the token-first precedence this needs, where the JWT is authoritative and
 * the {@code X-User-*} headers are honoured only when {@code cms.security.allow-dev-identity-headers} is
 * set. A CEPC copy of that rule would be the copy that drifts.
 *
 * <p>The practical consequence is that a caller on a deployed instance cannot name a CEPC role they do not
 * hold in order to widen the dashboard scope they are served.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CepcIdentityResolver {

    /**
     * CEPC roles in workflow-ladder order, lowest first — the same ordering convention as
     * {@code RbioRoles.ALL}.
     *
     * <p>{@code ADMIN} is deliberately absent. It is a cross-module role, so resolving it as "the caller's
     * CEPC role" would hand a platform administrator a dealing-officer's scoped worklist rather than the
     * unscoped view they should get; callers that need to recognise it read {@link #resolveRoles()}.
     */
    private static final List<String> CEPC_ROLES = List.of(
            "CEPC_DO",
            "CEPC_OFFICER",
            "CEPC_CONTACT_PERSON",
            "CEPC_CONCILIATOR",
            "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER",
            "CEPC_INCHARGE",
            "CEPC_CLOSING_AUTHORITY",
            "CEPC_SUPERVISOR",
            "CEPC_ADMIN");

    private final AaIdentityResolver delegate;

    /**
     * The caller's CEPC role, or null when they hold none.
     *
     * <p>Null rather than a default: defaulting to {@code CEPC_DO} would give an unroled caller a dealing
     * officer's view of the complaint table, and the fail-closed filter vocabulary depends on being able to
     * tell "no role" from "some role".
     */
    public String resolveCepcRole() {
        Set<String> held = delegate.resolveRoles();
        for (String candidate : CEPC_ROLES) {
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

    /** True when the caller holds a cross-module administrator role. */
    public boolean isAdmin() {
        Set<String> held = delegate.resolveRoles();
        return held.contains("ADMIN") || held.contains("CEPC_ADMIN");
    }
}
