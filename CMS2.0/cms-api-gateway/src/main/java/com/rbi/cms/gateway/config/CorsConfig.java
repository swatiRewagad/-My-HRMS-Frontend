package com.rbi.cms.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared CORS configuration for the API gateway, used by both the
 * default (non dev-local) and dev-local security filter chains so the
 * allowed origins never drift between profiles.
 */
@Configuration
public class CorsConfig {

    @Value("${cms.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${cms.cors.allowed-methods}")
    private String allowedMethods;

    @Value("${cms.cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${cms.cors.allow-credentials}")
    private boolean allowCredentials;

    @Value("${cms.cors.max-age}")
    private long maxAge;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(splitAndTrim(allowedOrigins));
        config.setAllowedMethods(splitAndTrim(allowedMethods));
        config.setAllowedHeaders(splitAndTrim(allowedHeaders));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private List<String> splitAndTrim(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
