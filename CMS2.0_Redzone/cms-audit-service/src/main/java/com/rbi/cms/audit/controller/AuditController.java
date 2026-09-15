package com.rbi.cms.audit.controller;

import com.rbi.cms.audit.entity.AuditLog;
import com.rbi.cms.audit.service.AuditService;
import com.rbi.cms.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Immutable audit trail APIs")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/{entityType}/{entityId}")
    @Operation(summary = "Get audit trail for entity", description = "Returns complete audit history for a given entity")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditTrail(
            @PathVariable String entityType,
            @PathVariable String entityId) {

        List<AuditLog> trail = auditService.getAuditTrail(entityType, entityId);
        return ResponseEntity.ok(ApiResponse.success(trail));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get audit by user", description = "Returns all actions performed by a specific user")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLog> result = auditService.getAuditByUser(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
