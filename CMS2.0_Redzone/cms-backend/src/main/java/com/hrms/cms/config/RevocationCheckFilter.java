package com.hrms.cms.config;

import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.CredentialRevocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests from users whose credentials have been revoked (UST877).
 *
 * A revoked user's already-issued access token stays cryptographically valid until it expires, and
 * this API validates tokens offline against the realm key rather than asking Keycloak per request.
 * Disabling the account in Keycloak therefore does not stop an in-flight token on its own — this
 * filter is what makes revocation take effect immediately.
 *
 * Runs after the Spring Security chain has authenticated the caller, so only genuine identities are
 * checked and anonymous public traffic is untouched.
 */
@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class RevocationCheckFilter extends OncePerRequestFilter {

    private final CredentialRevocationService credentialRevocationService;
    private final RequestIdentityResolver requestIdentityResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RequestIdentity identity = requestIdentityResolver.resolve(request);

        if (identity != null && credentialRevocationService.isRevoked(identity.getUserId())) {
            log.warn("Rejected request from revoked user {} to {}",
                    identity.getUserId(), request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"error\":\"CREDENTIALS_REVOKED\","
                            + "\"message\":\"Your access has been revoked. Contact your administrator.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/info");
    }
}
