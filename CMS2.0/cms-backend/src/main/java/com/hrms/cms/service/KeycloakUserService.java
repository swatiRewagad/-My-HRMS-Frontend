package com.hrms.cms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate;

    public KeycloakUserService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        this.restTemplate = new RestTemplate(factory);
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replace("'", "").replace("\"", "").trim();
    }

    private String getAdminToken() {
        String cleanUrl = sanitize(serverUrl);
        log.info("Keycloak serverUrl=[{}], realm=[{}], clientId=[{}], username=[{}]", cleanUrl, realm, clientId, adminUsername);
        String tokenUrl = cleanUrl + "/realms/master/protocol/openid-connect/token";

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
        return getUsersByRole("CRPC_REVIEWER");
    }

    public List<Map<String, Object>> getAllCrpcUsers() {
        List<Map<String, Object>> all = new ArrayList<>();
        all.addAll(getDeos());
        all.addAll(getReviewers());

        List<Map<String, Object>> heads = getUsersByRole("CRPC_HEAD");
        all.addAll(heads);

        return all;
    }

    /**
     * Every realm user as {@code username -> displayName}, for consumers that only need to render a name.
     *
     * <p>Deliberately not built from {@link #getAllCrpcUsers()}: that covers only DEO, CRPC_REVIEWER and
     * CRPC_HEAD, so no RBIO officer appears in it. Fetching per role instead would need a complete role
     * inventory that does not exist anywhere in the codebase, so this reads the realm's user list once.
     *
     * <p>Returns an empty map rather than throwing when Keycloak is unreachable — the caller uses this to
     * decorate output and has a username to fall back on.
     */
    public Map<String, String> getUserDirectory() {
        String token = getAdminToken();
        if (token == null) {
            log.warn("Cannot fetch user directory - no admin token available");
            return Collections.emptyMap();
        }

        // Keycloak defaults to 100 users per page, which silently truncates a larger realm.
        String url = serverUrl + "/admin/realms/" + realm + "/users?briefRepresentation=true&max=10000";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<List> response =
                    restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            if (response.getBody() == null) {
                return Collections.emptyMap();
            }

            Map<String, String> directory = new LinkedHashMap<>();
            for (Map<String, Object> keycloakUser : (List<Map<String, Object>>) response.getBody()) {
                String username = (String) keycloakUser.get("username");
                if (username != null && !username.isBlank()) {
                    directory.put(username, buildDisplayName(keycloakUser));
                }
            }
            return directory;
        } catch (Exception e) {
            log.error("Failed to fetch user directory: {}", e.getMessage());
            return Collections.emptyMap();
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

        // Extract officeCode from Keycloak attributes
        Map<String, Object> attributes = (Map<String, Object>) keycloakUser.getOrDefault("attributes", Collections.emptyMap());
        if (attributes != null && attributes.containsKey("officeCode")) {
            Object officeVal = attributes.get("officeCode");
            if (officeVal instanceof List && !((List<?>) officeVal).isEmpty()) {
                user.put("officeCode", ((List<?>) officeVal).get(0).toString());
            } else if (officeVal instanceof String) {
                user.put("officeCode", officeVal);
            }
        }
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
