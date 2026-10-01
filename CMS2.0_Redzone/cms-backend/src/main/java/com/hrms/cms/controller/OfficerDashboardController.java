package com.hrms.cms.controller;

import com.hrms.cms.service.DashboardService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")

public class OfficerDashboardController {

    private final DashboardService dashboardService;

    public OfficerDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePrivate())
            .body(dashboardService.getOfficerSummary());
    }

    @GetMapping("/summary/{department}")
    public ResponseEntity<Map<String, Object>> getDepartmentSummary(@PathVariable String department) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePrivate())
            .body(dashboardService.getDepartmentSummary(department));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshCache() {
        dashboardService.refreshCache();
        return ResponseEntity.ok(Map.of("status", "cache_cleared", "message", "Dashboard cache refreshed"));
    }
}
