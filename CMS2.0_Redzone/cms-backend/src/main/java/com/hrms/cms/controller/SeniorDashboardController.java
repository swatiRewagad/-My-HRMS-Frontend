package com.hrms.cms.controller;

import com.hrms.cms.service.dashboard.SeniorDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/senior-dashboard")
@RequiredArgsConstructor
public class SeniorDashboardController {

    private final SeniorDashboardService dashboardService;

    @GetMapping("/pipeline")
    public ResponseEntity<Map<String, Object>> getPipeline() {
        return ResponseEntity.ok(dashboardService.getPipelineSummary());
    }

    @GetMapping("/tat")
    public ResponseEntity<Map<String, Object>> getTat() {
        return ResponseEntity.ok(dashboardService.getTatAnalytics());
    }

    @GetMapping("/bottlenecks")
    public ResponseEntity<Map<String, Object>> getBottlenecks() {
        return ResponseEntity.ok(dashboardService.getBottlenecks());
    }

    @GetMapping("/trend")
    public ResponseEntity<List<Map<String, Object>>> getTrend() {
        return ResponseEntity.ok(dashboardService.getWeeklyTrend());
    }

    @GetMapping("/entity-performance")
    public ResponseEntity<Map<String, Object>> getEntityPerformance() {
        return ResponseEntity.ok(dashboardService.getEntityPerformance());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getFullSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pipeline", dashboardService.getPipelineSummary());
        summary.put("tat", dashboardService.getTatAnalytics());
        summary.put("bottlenecks", dashboardService.getBottlenecks());
        summary.put("trend", dashboardService.getWeeklyTrend());
        summary.put("entityPerformance", dashboardService.getEntityPerformance());
        return ResponseEntity.ok(summary);
    }
}
