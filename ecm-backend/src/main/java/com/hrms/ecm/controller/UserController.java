package com.hrms.ecm.controller;

import com.hrms.ecm.entity.EcmUser;
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

    @GetMapping
    public ResponseEntity<List<EcmUser>> getAllUsers() {
        return ResponseEntity.ok(ecmService.getAllUsers());
    }
}
