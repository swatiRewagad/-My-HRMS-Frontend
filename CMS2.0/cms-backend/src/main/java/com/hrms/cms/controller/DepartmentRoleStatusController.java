package com.hrms.cms.controller;

import com.hrms.cms.entity.RoleStatusMapping;
import com.hrms.cms.service.RoleStatusMappingService;
import com.rbi.cms.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentRoleStatusController {

    private final RoleStatusMappingService service;

    @GetMapping("/{department}/role-status")
    public ResponseEntity<ApiResponse<List<String>>> getStatusCodes(
            @PathVariable String department,
            @RequestParam String role) {

        List<String> codes = service.getStatusesByRole(role)
                .stream()
                .map(RoleStatusMapping::getStatusCode)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(codes));
    }
}
