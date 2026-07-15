package com.hrms.ecm.controller;

import com.hrms.ecm.entity.EcmUser;
import com.hrms.ecm.repository.EcmUserRepository;
import com.hrms.ecm.service.EcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final EcmService ecmService;
    private final EcmUserRepository userRepo;

    @GetMapping
    public ResponseEntity<List<EcmUser>> getAllUsers() {
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
