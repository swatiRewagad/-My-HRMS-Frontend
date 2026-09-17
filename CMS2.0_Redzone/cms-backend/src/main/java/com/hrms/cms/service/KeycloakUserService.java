package com.hrms.cms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KeycloakUserService {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    private final RestTemplate restTemplate = new RestTemplate();

    private String getAdminToken() {
        String tokenUrl = serverUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("username", adminUsername);
        body.add("password", adminPassword);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, request, Map.class);
            if (response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.error("Failed to get Keycloak admin token: {}", e.getMessage());
        }
        return null;
    }

    public List<Map<String, Object>> getUsersByRole(String roleName) {
        return getUsersByRole(roleName, true);
    }

    /**
     * Get users by role, optionally filtering out users with SECRETARY role (UST655).
     */
    public List<Map<String, Object>> getUsersByRole(String roleName, boolean excludeSecretary) {
        String token = getAdminToken();
        if (token == null) {
            log.warn("Cannot fetch users - no admin token available");
            return Collections.emptyList();
        }

        String url = serverUrl + "/admin/realms/" + realm + "/roles/" + roleName + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, request, List.class);
            if (response.getBody() != null) {
                List<Map<String, Object>> users = ((List<Map<String, Object>>) response.getBody()).stream()
                        .map(this::mapUser)
                        .collect(Collectors.toList());

                // UST655: Filter out users with SECRETARY role from assignment
                if (excludeSecretary) {
                    users = users.stream()
                            .filter(u -> {
                                String userId = (String) u.getOrDefault("userId", "");
                                return !userId.toLowerCase().contains("secretary");
                            })
                            .collect(Collectors.toList());
                }

                return users;
            }
        } catch (Exception e) {
            log.error("Failed to fetch users with role {}: {}", roleName, e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<Map<String, Object>> getDeos() {
        return getUsersByRole("DEO");
    }

    public List<Map<String, Object>> getReviewers() {
        return getUsersByRole("REVIEWER");
    }

    public List<Map<String, Object>> getAllCrpcUsers() {
        List<Map<String, Object>> all = new ArrayList<>();
        all.addAll(getDeos());
        all.addAll(getReviewers());

        List<Map<String, Object>> heads = getUsersByRole("CRPC_HEAD");
        all.addAll(heads);

        return all;
    }

    /** Looks up the internal Keycloak user id for a username; null when the user does not exist. */
    public String findUserId(String username) {
        String token = getAdminToken();
        if (token == null) {
            return null;
        }
        String url = serverUrl + "/admin/realms/" + realm + "/users?username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&exact=true";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            List<Map<String, Object>> users = response.getBody();
            if (users != null && !users.isEmpty()) {
                return (String) users.get(0).get("id");
            }
        } catch (Exception e) {
            log.error("Failed to look up Keycloak user {}: {}", username, e.getMessage());
        }
        return null;
    }

    public Map<String, Object> getUserById(String keycloakUserId) {
        String token = getAdminToken();
        if (token == null) {
            return null;
        }
        String url = serverUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to read Keycloak user {}: {}", keycloakUserId, e.getMessage());
            return null;
        }
    }

    /**
     * Disables the account so no new token can be issued (UST877).
     *
     * Returns false rather than throwing so the caller can decide: a revocation that silently
     * "succeeded" while the account stayed enabled would be the worst outcome here.
     */
    public boolean setUserEnabled(String keycloakUserId, boolean enabled) {
        String token = getAdminToken();
        if (token == null) {
            log.error("Cannot change enabled state for {} - no admin token", keycloakUserId);
            return false;
        }
        String url = serverUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.exchange(url, HttpMethod.PUT,
                    new HttpEntity<>(Map.of("enabled", enabled), headers), Void.class);
            log.info("Keycloak user {} enabled={}", keycloakUserId, enabled);
            return true;
        } catch (Exception e) {
            log.error("Failed to set enabled={} for Keycloak user {}: {}", enabled, keycloakUserId, e.getMessage());
            return false;
        }
    }

    /**
     * Terminates every active session, invalidating the refresh tokens behind them.
     *
     * Disabling alone is not revocation: this API is stateless and does not introspect on each
     * request, so an already-issued access token keeps working until it expires. Killing the
     * sessions stops the refresh cycle that would otherwise keep renewing access indefinitely.
     */
    public boolean logoutAllSessions(String keycloakUserId) {
        String token = getAdminToken();
        if (token == null) {
            log.error("Cannot log out sessions for {} - no admin token", keycloakUserId);
            return false;
        }
        String url = serverUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/logout";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), Void.class);
            log.info("All Keycloak sessions terminated for user {}", keycloakUserId);
            return true;
        } catch (Exception e) {
            log.error("Failed to terminate sessions for Keycloak user {}: {}", keycloakUserId, e.getMessage());
            return false;
        }
    }

    public int countActiveSessions(String keycloakUserId) {
        String token = getAdminToken();
        if (token == null) {
            return -1;
        }
        String url = serverUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/sessions";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            return response.getBody() == null ? 0 : response.getBody().size();
        } catch (Exception e) {
            log.error("Failed to count sessions for Keycloak user {}: {}", keycloakUserId, e.getMessage());
            return -1;
        }
    }

    private Map<String, Object> mapUser(Map<String, Object> keycloakUser) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", keycloakUser.get("username"));
        user.put("id", keycloakUser.get("id"));
        user.put("displayName", buildDisplayName(keycloakUser));
        user.put("email", keycloakUser.getOrDefault("email", ""));
        user.put("firstName", keycloakUser.getOrDefault("firstName", ""));
        user.put("lastName", keycloakUser.getOrDefault("lastName", ""));
        user.put("enabled", keycloakUser.getOrDefault("enabled", true));
        return user;
    }

    private String buildDisplayName(Map<String, Object> user) {
        String first = (String) user.getOrDefault("firstName", "");
        String last = (String) user.getOrDefault("lastName", "");
        if (!first.isEmpty() || !last.isEmpty()) {
            return (first + " " + last).trim();
        }
        return (String) user.getOrDefault("username", "Unknown");
    }
}
