package com.hrms.ecm.controller;

import com.hrms.ecm.entity.EcmUser;
import com.hrms.ecm.repository.EcmUserRepository;
import com.hrms.ecm.service.EcmService;
import com.hrms.ecm.service.KeycloakUserFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final EcmService ecmService;
    private final EcmUserRepository userRepo;
    private final KeycloakUserFetcher keycloakUserFetcher;

    @GetMapping
    public ResponseEntity<?> getUsers(
            @RequestHeader(value = "X-Realm", required = false) String realm) {
        if (realm != null && !realm.isBlank()) {
            List<Map<String, Object>> realmUsers = keycloakUserFetcher.getUsersFromRealm(realm);
            return ResponseEntity.ok(realmUsers);
        }
        return ResponseEntity.ok(ecmService.getAllUsers());
    }

    @GetMapping("/me")
    public ResponseEntity<EcmUser> getCurrentUser(@RequestHeader(value = "X-Username", required = false) String username) {
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return userRepo.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
