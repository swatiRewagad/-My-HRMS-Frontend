package com.hrms.cms.controller;

import com.hrms.cms.service.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/keycloak")
@RequiredArgsConstructor
public class KeycloakUserController {

    private final KeycloakUserService keycloakUserService;

    @GetMapping("/users/deos")
    public Map<String, Object> getDeos() {
        List<Map<String, Object>> deos = keycloakUserService.getDeos();
        List<Map<String, Object>> enriched = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> deo : deos) {
            Map<String, Object> enrichedDeo = new LinkedHashMap<>(deo);
            enrichedDeo.put("isActive", Boolean.TRUE.equals(deo.get("enabled")));
            enrichedDeo.put("isOnLeave", false);
            enrichedDeo.put("maxThreshold", 20);
            enrichedDeo.put("currentAssignedCount", 0);
            enrichedDeo.put("sortOrder", sortOrder++);
            enriched.add(enrichedDeo);
        }
        return wrapResponse(enriched);
    }

    @GetMapping("/users/reviewers")
    public Map<String, Object> getReviewers() {
        List<Map<String, Object>> reviewers = keycloakUserService.getReviewers();
        List<Map<String, Object>> enriched = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> reviewer : reviewers) {
            Map<String, Object> enrichedReviewer = new LinkedHashMap<>(reviewer);
            enrichedReviewer.put("isActive", Boolean.TRUE.equals(reviewer.get("enabled")));
            enrichedReviewer.put("isOnLeave", false);
            enrichedReviewer.put("maxLoad", 25);
            enrichedReviewer.put("currentLoad", 0);
            enrichedReviewer.put("region", "");
            enrichedReviewer.put("sortOrder", sortOrder++);
            enriched.add(enrichedReviewer);
        }
        return wrapResponse(enriched);
    }

    @GetMapping("/users/all")
    public Map<String, Object> getAllCrpcUsers() {
        return wrapResponse(keycloakUserService.getAllCrpcUsers());
    }

    @GetMapping("/users/by-role")
    public List<Map<String, Object>> getUsersByRole(@RequestParam String role) {
        return keycloakUserService.getUsersByRole(role);
    }

    private Map<String, Object> wrapResponse(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", data);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}
