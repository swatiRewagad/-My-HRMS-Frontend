package com.rbi.cms.search.config;

import com.rbi.cms.common.enums.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OfficerJwtAuthenticationConverter officerJwtAuthenticationConverter;

    /**
     * Note the paths carry no {@code /cms-search} prefix: the servlet context path is stripped before
     * matchers are evaluated, so including it would silently match nothing and fall through to
     * {@code denyAll}.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Safe to disable for a stateless, bearer-token API: with no cookie or session to ride
                // on, there is no ambient credential for a forged cross-site request to borrow.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        // A reindex rewrites the whole corpus and is expensive enough to be a
                        // denial-of-service primitive on its own, so it is admin-only.
                        .requestMatchers(HttpMethod.POST, "/api/v1/search/complaints/reindex/**")
                                .hasRole(RoleConstants.RBIO_ADMIN)
                        .requestMatchers("/api/v1/search/complaints/reindex/jobs/**")
                                .hasRole(RoleConstants.RBIO_ADMIN)
                        .requestMatchers("/api/v1/search/**").authenticated()
                        // Default-deny, so a newly added endpoint is unreachable until it is
                        // deliberately classified rather than being exposed by omission.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(officerJwtAuthenticationConverter)))
                .build();
    }
}
