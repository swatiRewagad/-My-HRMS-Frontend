package com.rbi.cms.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@Profile("!dev-local")
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtAuthConverter keycloakJwtAuthConverter;

    @Value("${cms.cors.allowed-origins:https://cms-portal-frontend-rbi-cms.apps.ocpvcl.rebit.org.in}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/i18n/**").permitAll()
                        .requestMatchers("/api/v1/translations/**").permitAll()
                        .requestMatchers("/api/v1/eligibility/**").permitAll()
                        .requestMatchers("/api/v1/citizen/**").permitAll()
                        .requestMatchers("/api/v1/complaints/**").permitAll()
                        .requestMatchers("/api/v1/geo/**").permitAll()
                        .requestMatchers("/api/v1/masters/**").permitAll()
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
