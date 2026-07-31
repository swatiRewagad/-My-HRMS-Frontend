package com.hrms.ecm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class KeycloakUserFetcher {

    @Value("${ecm.keycloak.url:http://localhost:9090}")
    private String keycloakUrl;

    @Value("${ecm.keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${ecm.keycloak.admin-password:admin}")
    private String adminPassword;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Map<String, Object>> getUsersFromRealm(String realm) {
        String token = getAdminToken();
        if (token == null) return Collections.emptyList();

        String url = keycloakUrl + "/admin/realms/" + realm + "/users?max=100";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        List<Map<String, Object>> result = new ArrayList<>();
        if (response.getBody() != null) {
            for (Map<String, Object> kcUser : response.getBody()) {
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("id", kcUser.get("id"));
                user.put("username", kcUser.get("username"));
                user.put("displayName", buildDisplayName(kcUser));
                user.put("email", kcUser.getOrDefault("email", ""));
                user.put("firstName", kcUser.getOrDefault("firstName", ""));
                user.put("lastName", kcUser.getOrDefault("lastName", ""));
                result.add(user);
            }
        }
        return result;
    }

    private String buildDisplayName(Map<String, Object> kcUser) {
        String first = (String) kcUser.getOrDefault("firstName", "");
        String last = (String) kcUser.getOrDefault("lastName", "");
        String display = (first + " " + last).trim();
        return display.isEmpty() ? (String) kcUser.get("username") : display;
    }

    private String getAdminToken() {
        String url = keycloakUrl + "/realms/master/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "admin-cli");
        body.add("username", adminUsername);
        body.add("password", adminPassword);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            if (response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            // log error silently
        }
        return null;
    }
}
