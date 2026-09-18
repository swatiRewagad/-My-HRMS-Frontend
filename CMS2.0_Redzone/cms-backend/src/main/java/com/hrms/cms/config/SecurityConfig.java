package com.hrms.cms.config;

import com.hrms.cms.security.KeycloakJwtAuthConverter;
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

import java.util.Arrays;
import java.util.List;

/**
 * Enforcing security chain for every profile except dev-local.
 *
 * Citizen-facing paths stay anonymous because a complainant files and tracks without an account.
 * Everything else requires a verified Keycloak token: cms-backend is reached directly by the
 * browsers (both frontends point at it, and the gateway has no route to it), so there is no
 * trusted upstream hop and the X-User-* dev headers must never be authoritative here.
 *
 * Role names are matched without the ROLE_ prefix; {@link KeycloakJwtAuthConverter} adds it when
 * mapping realm_access.roles. Only roles that exist in the realm are referenced.
 */
@Configuration
@EnableWebSecurity
@Profile("!dev-local")
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] RBIO_ROLES = {
            "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "ADMIN"
    };

    private static final String[] CEPC_ROLES = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_SUPERVISOR",
            "CEPC_CLOSING_AUTHORITY", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_CONTACT_PERSON", "CEPC_ADMIN", "ADMIN"
    };

    private static final String[] CRPC_ROLES = {
            "CRPC_HEAD", "CRPC_ADMIN", "CRPC_INCHARGE", "DEO", "REVIEWER", "ADMIN"
    };

    private static final String[] STAFF_ROLES = {
            "OFFICER", "DEO", "REVIEWER", "TOLL_FREE_HELPDESK",
            "CRPC_HEAD", "CRPC_ADMIN", "CRPC_INCHARGE",
            "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR",
            "CEPC_DO", "CEPC_OFFICER", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_SUPERVISOR",
            "CEPC_CLOSING_AUTHORITY", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_CONTACT_PERSON", "CEPC_ADMIN", "ADMIN"
    };

    private final KeycloakJwtAuthConverter keycloakJwtAuthConverter;

    @Value("${cms.cors.allowed-origins:http://localhost:4200,http://localhost:4201,http://localhost:4202,http://localhost:4300}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── Anonymous: citizen self-service and reference data ──────────────────
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/citizen/**").permitAll()
                .requestMatchers("/api/v1/eligibility/**").permitAll()
                .requestMatchers("/api/v1/faq/**").permitAll()
                .requestMatchers("/api/v1/i18n/**", "/api/v1/translations/**").permitAll()
                .requestMatchers("/api/v1/geo/**", "/api/v1/location/**", "/api/v1/pincodes/**").permitAll()
                .requestMatchers("/api/v1/masters/**", "/api/v1/entities/**", "/api/v1/categories/**").permitAll()
                .requestMatchers("/api/categories/**", "/api/banks/**", "/api/form-config/**").permitAll()
                .requestMatchers("/api/v1/feedback/**").permitAll()
                .requestMatchers("/api/v1/complaints/drafts/**").permitAll()
                // Filing and tracking are deliberately open (UST98/UST99); complainant PII in
                // these responses is masked by PiiMaskingService, not withheld by this chain.
                .requestMatchers(HttpMethod.POST, "/api/v1/complaints").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/complaints/check-duplicate").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/complaints/recent").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/complaints/{complaintNumber:[A-Za-z0-9\\-/]+}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/complaints").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/complaints/*/withdraw").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/complaints/track/**").permitAll()

                // Secure upload link: the COMPLAINANT-facing half only (UST598-600).
                //
                // These three must be anonymous because the recipient is a citizen following a link from
                // an email — they have no Keycloak account and never will. The whole feature was
                // unreachable without this: /api/v1/upload-link/** sits in the staff block below, so
                // every request from the citizen page was rejected before it reached the controller.
                //
                // The TOKEN is the credential, and it is not sufficient on its own: request-otp and
                // validate-otp enforce the dual OTP, and /upload refuses until otpVerifiedAt is set.
                // /send and /revoke are deliberately NOT listed — creating or killing a link is a staff
                // action and stays behind the staff matcher.
                .requestMatchers(HttpMethod.POST, "/api/v1/upload-link/request-otp").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/upload-link/validate-otp").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/upload-link/upload/*").permitAll()

                // ── Administrative: user management, config, security console ───────────
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/keycloak/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // ── Workflow, scoped per office ─────────────────────────────────────────
                .requestMatchers("/api/v1/workflow/rbio/**").hasAnyRole(RBIO_ROLES)
                .requestMatchers("/api/v1/workflow/cepc/**").hasAnyRole(CEPC_ROLES)
                .requestMatchers("/api/v1/crpc/**").hasAnyRole(CRPC_ROLES)

                // ── Remaining staff surfaces ────────────────────────────────────────────
                .requestMatchers("/api/v1/workflow/**", "/api/v1/appeals/**", "/api/v1/re-portal/**",
                        "/api/v1/complaint-queries/**", "/api/v1/reports/**", "/api/v1/dashboard/**",
                        "/api/v1/senior-dashboard/**", "/api/v1/past-complaints/**",
                        "/api/v1/email-syndication/**", "/api/v1/triage/**", "/api/v1/rules/**",
                        "/api/v1/mre/**", "/api/v1/routing/**", "/api/v1/tat/**",
                        "/api/v1/notifications/**", "/api/v1/upload-link/**",
                        "/api/v1/comment-templates/**", "/api/v1/communication-templates/**",
                        "/api/v1/extraction-rules/**", "/api/v1/similar-cases/**",
                        "/api/v1/copilot/**", "/api/v1/ocr/**", "/api/v1/re-activity-config/**",
                        "/api/complaints/**", "/api/dashboard/**", "/api/files/**",
                        "/api/email-simulation/**").hasAnyRole(STAFF_ROLES)

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthConverter)));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
