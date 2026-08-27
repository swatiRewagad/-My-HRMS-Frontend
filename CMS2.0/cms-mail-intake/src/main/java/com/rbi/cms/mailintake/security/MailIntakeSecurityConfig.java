package com.rbi.cms.mailintake.security;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every {@code /admin/mail-intake/**} endpoint validates a real, signature-checked JWT against
 * Keycloak's JWKS (same realm — {@code rbi-cms} — the rest of CMS uses) and requires the
 * configurable {@code cms.mail.intake.admin.required-role} authority, extracted from the token's
 * own {@code realm_access.roles} claim. Deliberately NOT the {@code cms-backend} pattern
 * ({@code SecurityConfig} there is {@code permitAll()} on literally everything, including
 * {@code /api/v1/admin/**}) — this is the one place in the intake module that can replay mail
 * into the CMS pipeline or relabel its origin, so it gets real authorization from day one rather
 * than inheriting a known gap. Mirrors {@code cms-assignment-service}'s resource-server wiring and
 * {@code KeycloakJwtConfig}'s role-claim shape (not reused directly as a dependency — that class
 * lives in {@code cms-infrastructure}, which pulls in spring-kafka; this module has no Kafka
 * dependency and shouldn't gain one just for a five-line converter).
 *
 * The SMTP listener (port {@code cms.mail.intake.listener.port}) is a raw Netty server, not a
 * servlet — this filter chain has no effect on it and never will.
 *
 * dev-local deliberately has no {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} set
 * (no local Keycloak is part of this module's dev-local stack) — Spring Boot's resource-server
 * auto-configuration only registers a {@code JwtDecoder} bean when that property (or
 * {@code jwk-set-uri}) is present, and decoder resolution itself is deferred until a request
 * actually needs it, so the five existing {@code @SpringBootTest} tests (none of which touch an
 * HTTP admin endpoint) keep passing unmodified. To exercise the admin endpoints against a local
 * Keycloak, set {@code SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI} for that run.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class MailIntakeSecurityConfig {

    private final MailIntakeProperties properties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String requiredAuthority = "ROLE_" + properties.getAdmin().getRequiredRole().toUpperCase(Locale.ROOT);

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/admin/mail-intake/**").hasAuthority(requiredAuthority)
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(realmRolesConverter());
        return converter;
    }

    private Converter<Jwt, Collection<GrantedAuthority>> realmRolesConverter() {
        return jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null) {
                return Collections.emptyList();
            }
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles == null) {
                return Collections.emptyList();
            }
            return roles.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        };
    }
}
