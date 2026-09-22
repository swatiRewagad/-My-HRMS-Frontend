package com.hrms.cms.security;

/**
 * Raised when a caller has no AA role, or holds a role that may not perform the requested action.
 *
 * Distinct from IllegalArgumentException so an authorization failure surfaces as 403 rather than
 * being folded into the generic validation path, which returned HTTP 200 with success=false. A test
 * asserting "denied" against a 200 body cannot distinguish denial from a mistyped field, which is
 * how the original blank-role bypass survived unnoticed.
 */
public class AaAccessDeniedException extends RuntimeException {

    public AaAccessDeniedException(String message) {
        super(message);
    }
}
