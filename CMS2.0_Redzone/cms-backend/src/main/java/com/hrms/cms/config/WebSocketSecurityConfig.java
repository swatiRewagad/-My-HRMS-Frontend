package com.hrms.cms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lets the websocket handshake through the HTTP chain, so that authentication can happen one layer
 * later on the STOMP CONNECT frame instead.
 *
 * <p>WHY: a browser cannot attach an {@code Authorization} header to a websocket upgrade — the
 * WebSocket constructor takes a URL and a subprotocol, nothing else. {@link SecurityConfig} ends in
 * {@code anyRequest().authenticated()}, so the handshake would be answered with 401 and the bell
 * could never connect on any profile except dev-local. Putting the token in the query string is the
 * usual workaround and is rejected here: it lands in access logs and browser history.
 *
 * <p>This is not a hole. The upgrade establishes a transport with no identity and no subscription;
 * {@code StompIdentityChannelInterceptor} then requires a verified token (or, under dev-local only, a
 * dev header) on CONNECT and fails the frame otherwise, and the broker will not expand
 * {@code /user/queue/**} for a session with no principal. An unauthenticated socket can therefore
 * open and then receive nothing.
 *
 * <p>Ordered ahead of the profile chains and scoped strictly to {@code /ws/**}; every other path
 * still falls through to {@link SecurityConfig} or {@link DevLocalSecurityConfig} unchanged. Not
 * profile-bound, because both profiles need the same handshake behaviour.
 */
@Configuration
public class WebSocketSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public SecurityFilterChain webSocketHandshakeFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/ws/**")
            // Reuses whichever CorsConfigurationSource the active profile published, so the allowed
            // origins for a socket and for the REST API cannot drift apart.
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
