package com.hrms.cms.controller;

import com.hrms.cms.service.CrpcInChargeDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/crpc/in-charge")
@PreAuthorize("hasAnyRole('CRPC_INCHARGE', 'CRPC_HEAD', 'ADMIN')")
@RequiredArgsConstructor
public class CrpcInChargeController {

    private final CrpcInChargeDashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getPanIndiaSummary() {
        return ResponseEntity.ok(dashboardService.getPanIndiaSummary());
    }

    @GetMapping("/office/{officeId}")
    public ResponseEntity<Map<String, Object>> getOfficeStats(@PathVariable String officeId) {
        return ResponseEntity.ok(dashboardService.getOfficeWiseStats(officeId));
    }

    @GetMapping("/deo-workload")
    public ResponseEntity<Map<String, Object>> getDeoWorkload() {
        return ResponseEntity.ok(dashboardService.getDeoWorkload());
    }
}
