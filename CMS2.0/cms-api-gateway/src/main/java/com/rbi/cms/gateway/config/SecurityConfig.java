package com.rbi.cms.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@Profile("!dev-local")
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtAuthConverter keycloakJwtAuthConverter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/eligibility/**").permitAll()
                        .requestMatchers("/api/v1/complaints").permitAll()
                        .requestMatchers("/api/v1/complaints/{id}").permitAll()
                        .requestMatchers("/api/v1/complaints/*/track").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/workflow/rbio/**").hasAnyRole("RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "ADMIN")
                        .requestMatchers("/api/v1/workflow/cepc/**").hasAnyRole("CEPC_OFFICER", "CEPC_SUPERVISOR", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR", "ADMIN")
                        .requestMatchers("/api/v1/workflow/**").hasAnyRole("OFFICER", "DEO", "REVIEWER", "CRPC_HEAD", "CRPC_ADMIN", "CRPC_INCHARGE", "ADMIN")
                        .requestMatchers("/api/v1/assignment/**").hasAnyRole("OFFICER", "ADMIN", "RBIO_SUPERVISOR", "CEPC_SUPERVISOR")
                        .requestMatchers("/api/v1/audit/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthConverter)));

        return http.build();
    }
}
