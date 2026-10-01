package com.hrms.cms.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Puts a Principal on every STOMP session, which is what makes
 * {@code convertAndSendToUser(targetUserId, "/queue/notifications", ...)} deliverable.
 *
 * <p>WHY THIS EXISTS: the broker was registered with no handshake or channel interceptor anywhere in
 * the repo, so a websocket session had no user attached. Spring resolves {@code /user/queue/**} by
 * appending the session's principal name to the destination, so with no principal there was no
 * user-destination for a client to subscribe to and every push a producer made was discarded. The
 * bell therefore only ever changed on a page load.
 *
 * <p>IDENTITY: the principal name is the token's {@code preferred_username}, deliberately the same
 * value {@link AaIdentityResolver#resolveActor()} returns and the same value producers write into
 * {@code IN_APP_NOTIFICATIONS.TARGET_USER_ID} (business usernames such as {@code aa_do_001} or
 * {@code deo_001}; {@code KeycloakUserService.mapUser} publishes the Keycloak <em>username</em> as
 * {@code userId}). The Keycloak UUID ({@code sub}) matches none of those rows, so it is used only as
 * a last resort for a token that somehow carries no {@code preferred_username}.
 *
 * <p>AUTHENTICATION: the CONNECT frame's {@code Authorization: Bearer …} header is verified with the
 * application's own {@link JwtDecoder} — signature, issuer and expiry — not merely base64-decoded.
 * The decoder is resolved through an {@link ObjectProvider} because the dev-local profile excludes
 * {@code OAuth2ResourceServerAutoConfiguration} and therefore has no decoder bean at all.
 *
 * <p>DEV IDENTITY: an {@code X-User-Id} CONNECT header is honoured only while
 * {@code cms.security.allow-dev-identity-headers} is true, mirroring {@link AaIdentityResolver}. It
 * is unset outside dev-local, so a deployed instance cannot be talked into accepting a self-declared
 * websocket identity.
 *
 * <p>Anything this cannot identify is rejected: CONNECT fails, the client gets an ERROR frame and no
 * session is established. A permissive fallback would attach an attacker-chosen principal and hand
 * them another user's notification queue.
 */
@Component
@Slf4j
public class StompIdentityChannelInterceptor implements ChannelInterceptor {

    /** CONNECT header carrying the bearer token. Same name and shape as the HTTP header. */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** CONNECT header carrying a self-declared identity. Ignored unless the dev flag is on. */
    public static final String DEV_USER_HEADER = "X-User-Id";

    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;

    @Value("${cms.security.allow-dev-identity-headers:false}")
    private boolean allowDevIdentityHeaders;

    public StompIdentityChannelInterceptor(ObjectProvider<JwtDecoder> jwtDecoderProvider) {
        this.jwtDecoderProvider = jwtDecoderProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Only CONNECT carries credentials; every later frame inherits the session's principal.
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String userId = resolveUserId(accessor);
        if (userId == null) {
            throw new MessageDeliveryException(message,
                    "STOMP CONNECT rejected: no verifiable user identity on the frame");
        }

        accessor.setUser(new StompPrincipal(userId));
        log.debug("STOMP session {} bound to principal {}", accessor.getSessionId(), userId);
        return message;
    }

    private String resolveUserId(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
            if (decoder == null) {
                // dev-local has no decoder. Fall through to the dev header rather than trusting an
                // unverified token, and reject outright if that path is closed.
                log.debug("STOMP CONNECT presented a token but no JwtDecoder is configured");
            } else {
                return fromVerifiedToken(decoder, authHeader.substring(BEARER_PREFIX.length()).trim());
            }
        }

        if (!allowDevIdentityHeaders) {
            return null;
        }
        return blankToNull(accessor.getFirstNativeHeader(DEV_USER_HEADER));
    }

    private String fromVerifiedToken(JwtDecoder decoder, String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String preferredUsername = blankToNull(jwt.getClaimAsString("preferred_username"));
            if (preferredUsername != null) {
                return preferredUsername;
            }
            // No preferred_username is abnormal. The subject matches no notification row, but a
            // session with a stable principal is still better than none for future producers.
            log.warn("STOMP CONNECT token has no preferred_username; falling back to subject {}",
                    jwt.getSubject());
            return blankToNull(jwt.getSubject());
        } catch (Exception e) {
            log.debug("STOMP CONNECT token rejected: {}", e.getMessage());
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Minimal principal: the broker only ever reads {@link Principal#getName()} when resolving a
     * user destination, and authorisation for websocket frames is not modelled here.
     */
    record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
