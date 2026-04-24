package com.hrms.ecm.controller;

import com.hrms.ecm.dto.DashboardResponse;
import com.hrms.ecm.service.EcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final EcmService ecmService;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(ecmService.getDashboard());
    }
}
