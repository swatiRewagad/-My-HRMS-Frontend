package com.hrms.cms.security;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * The caller's identity as resolved by the server from the request.
 *
 * Author attribution on a query message must never come from the request body: UST857 requires
 * the thread to be tamper-evident, and a client-supplied author name would let any caller post
 * as anyone. {@link RequestIdentityResolver} derives this from the JWT (or the dev headers the
 * rest of this codebase already honours) and controllers pass it down.
 */
@Getter
@Builder
public class RequestIdentity {

    private final String userId;
    private final String displayName;
    private final String primaryRole;
    private final Set<String> roles;

    /** RE or RBI — which side of a query thread this caller speaks for. */
    private final String side;

    /** The entity an RE caller belongs to; null for RBI callers, who are not entity-scoped. */
    private final String entityCode;

    public boolean isRe() {
        return "RE".equals(side);
    }

    public boolean isRbi() {
        return "RBI".equals(side);
    }

    public boolean hasAnyRole(String... candidates) {
        for (String candidate : candidates) {
            if (roles.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
