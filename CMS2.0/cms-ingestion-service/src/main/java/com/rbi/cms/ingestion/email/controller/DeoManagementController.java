package com.rbi.cms.ingestion.email.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.ingestion.email.dto.DeoUserResponse;
import com.rbi.cms.ingestion.email.entity.DeoUser;
import com.rbi.cms.ingestion.email.repository.DeoUserRepository;
import com.rbi.cms.ingestion.email.service.RoundRobinAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/email-syndication/deo")
@RequiredArgsConstructor
@Tag(name = "DEO Management", description = "Manage DEO users, thresholds, and round-robin assignment")
public class DeoManagementController {

    private final DeoUserRepository deoUserRepository;
    private final RoundRobinAssignmentService assignmentService;

    @GetMapping
    @Operation(summary = "List all DEO users")
    public ResponseEntity<ApiResponse<List<DeoUserResponse>>> getAllDeos() {
        List<DeoUserResponse> deos = deoUserRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(deos, "DEO list fetched"));
    }

    @GetMapping("/eligible")
    @Operation(summary = "List eligible DEOs for assignment")
    public ResponseEntity<ApiResponse<List<DeoUserResponse>>> getEligibleDeos() {
        List<DeoUserResponse> deos = deoUserRepository.findEligibleDeos().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(deos, "Eligible DEOs fetched"));
    }

    @PostMapping
    @Operation(summary = "Add a new DEO user")
    public ResponseEntity<ApiResponse<DeoUserResponse>> addDeo(@RequestBody DeoUser deo) {
        deo = deoUserRepository.save(deo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(deo), "DEO added"));
    }

    @PutMapping("/{userId}/threshold")
    @Operation(summary = "Update DEO threshold")
    public ResponseEntity<ApiResponse<DeoUserResponse>> updateThreshold(
            @PathVariable String userId,
            @RequestParam Integer threshold) {

        DeoUser deo = deoUserRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("DEO not found: " + userId));
        deo.setMaxThreshold(threshold);
        deoUserRepository.save(deo);
        return ResponseEntity.ok(ApiResponse.success(toResponse(deo), "Threshold updated"));
    }

    @PutMapping("/{userId}/status")
    @Operation(summary = "Toggle DEO active/inactive or leave status")
    public ResponseEntity<ApiResponse<DeoUserResponse>> updateStatus(
            @PathVariable String userId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean onLeave) {

        DeoUser deo = deoUserRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("DEO not found: " + userId));

        if (active != null) deo.setIsActive(active);
        if (onLeave != null) deo.setIsOnLeave(onLeave);

        deoUserRepository.save(deo);
        return ResponseEntity.ok(ApiResponse.success(toResponse(deo), "DEO status updated"));
    }

    @PostMapping("/reset-pointer")
    @Operation(summary = "Reset round-robin pointer (queue refresh)")
    public ResponseEntity<ApiResponse<Void>> resetPointer() {
        assignmentService.resetPointer();
        return ResponseEntity.ok(ApiResponse.success(null, "Round-robin pointer reset"));
    }

    private DeoUserResponse toResponse(DeoUser deo) {
        return DeoUserResponse.builder()
                .id(deo.getId())
                .userId(deo.getUserId())
                .displayName(deo.getDisplayName())
                .email(deo.getEmail())
                .isActive(deo.getIsActive())
                .isOnLeave(deo.getIsOnLeave())
                .maxThreshold(deo.getMaxThreshold())
                .currentAssignedCount(deo.getCurrentAssignedCount())
                .sortOrder(deo.getSortOrder())
                .build();
    }
}
