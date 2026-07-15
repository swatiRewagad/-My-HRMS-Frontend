package com.hrms.ecm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/realms")
public class RealmController {

    @Value("${ecm.keycloak.url:http://localhost:9091}")
    private String keycloakUrl;

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> getAvailableRealms() {
        return ResponseEntity.ok(List.of(
            Map.of("name", "ecm", "displayName", "ECM - Enterprise Content Management", "url", keycloakUrl),
            Map.of("name", "rbi-cms", "displayName", "CMS - Complaint Management System", "url", keycloakUrl)
        ));
    }
}
