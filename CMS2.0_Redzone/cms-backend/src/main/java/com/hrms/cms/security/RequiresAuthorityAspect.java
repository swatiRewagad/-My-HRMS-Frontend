package com.hrms.cms.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/**
 * Enforces {@link RequiresAuthority}. Fails closed on every path.
 *
 * <h2>The three refusals, and why each exists</h2>
 * <ol>
 *   <li><b>No resolvable principal</b> — an unreadable or absent token. Proceeding would run the endpoint
 *       as nobody, and for a list endpoint that means serving every record to an unauthenticated caller.</li>
 *   <li><b>Authority not held</b> — the SSO did not grant this capability. This is the ordinary case and
 *       the one that makes UST629's "closure eligibility defaults to No" true by construction: a Reviewer
 *       who was never granted {@code RBIO_COMPLAINT_CLOSE_FINAL} cannot close, and nobody had to remember
 *       to set a flag.</li>
 *   <li><b>Entity scope required but absent</b> — an endpoint that acts for a specific Regulated Entity,
 *       called by a principal with no {@code entity_code}. Refused rather than run unscoped, because an
 *       unscoped query on those endpoints returns every entity's data.</li>
 * </ol>
 *
 * <h2>Why 403 and not 401</h2>
 * The token was accepted by the resource server; what is missing is a grant. Answering 401 would tell the
 * client to re-authenticate, which will not help and hides a mapping error behind an apparent login
 * problem. The message names the required authority so an administrator can find the missing SSO mapping
 * without reading the source.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequiresAuthorityAspect {

    private final CmsPrincipalResolver principalResolver;

    @Around("@annotation(requiresAuthority)")
    public Object enforce(ProceedingJoinPoint joinPoint, RequiresAuthority requiresAuthority) throws Throwable {
        CmsAuthority[] required = requiresAuthority.value();
        String target = joinPoint.getSignature().toShortString();

        // An empty list would otherwise admit everyone on the "no requirement" reading. It is treated as a
        // configuration error and refused: an endpoint annotated as guarded must never be open.
        if (required == null || required.length == 0) {
            log.error("{} is annotated @RequiresAuthority with no authority — refusing rather than "
                    + "admitting every caller", target);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: this action has no authority configured");
        }

        CmsPrincipalResolver.Principal principal = principalResolver.resolve();

        if (principal.isAnonymous()) {
            log.warn("Access denied to {}: no principal could be resolved from the request", target);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: your session could not be identified. Please sign in again.");
        }

        if (requiresAuthority.requiresEntityScope() && !principal.isRegulatedEntity()) {
            log.warn("Access denied to {}: {} is not a Regulated Entity principal", target, principal.username());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: this action can only be performed by a regulated entity user.");
        }

        for (CmsAuthority authority : required) {
            if (principal.has(authority)) {
                return joinPoint.proceed();
            }
        }

        // The granted set is logged, not returned. Telling a caller which authorities they hold is an aid
        // to probing; an administrator can read it from the log.
        log.warn("Access denied to {} for {}: holds {} but needs one of {}",
                target, principal.username(), principal.authorities(), Arrays.toString(required));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Access denied: this action requires the " + required[0].authority() + " authority.");
    }
}
