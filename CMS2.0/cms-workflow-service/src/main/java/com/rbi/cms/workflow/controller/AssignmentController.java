package com.rbi.cms.workflow.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.workflow.entity.OfficerPool;
import com.rbi.cms.workflow.repository.OfficerPoolRepository;
import com.rbi.cms.workflow.service.RoundRobinAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assignment")
@RequiredArgsConstructor
@Tag(name = "Assignment", description = "Officer pool and round-robin assignment management")
public class AssignmentController {

    private final OfficerPoolRepository officerPoolRepository;
    private final RoundRobinAssignmentService assignmentService;

    @GetMapping("/pool")
    @Operation(summary = "Get officer pool", description = "List all officers in a role group")
    public ResponseEntity<ApiResponse<List<OfficerPool>>> getPool(
            @RequestParam String roleGroup) {
        List<OfficerPool> officers = officerPoolRepository.findByRoleGroupAndActiveTrue(roleGroup);
        return ResponseEntity.ok(ApiResponse.success(officers));
    }

    @PostMapping("/pool")
    @Operation(summary = "Add officer to pool", description = "Register an officer for round-robin assignment")
    public ResponseEntity<ApiResponse<OfficerPool>> addToPool(@RequestBody OfficerPool officer) {
        officer.setActive(true);
        officer.setOnLeave(false);
        officer.setCurrentWorkload(0);
        OfficerPool saved = officerPoolRepository.save(officer);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PutMapping("/pool/{id}/leave")
    @Operation(summary = "Toggle leave status", description = "Mark officer as on-leave or returned")
    public ResponseEntity<ApiResponse<Void>> toggleLeave(
            @PathVariable Long id,
            @RequestParam boolean onLeave) {
        officerPoolRepository.findById(id).ifPresent(officer -> {
            officer.setOnLeave(onLeave);
            officerPoolRepository.save(officer);
        });
        return ResponseEntity.ok(ApiResponse.success(null,
                onLeave ? "Officer marked on leave" : "Officer returned from leave"));
    }

    @PutMapping("/pool/{id}/deactivate")
    @Operation(summary = "Deactivate officer", description = "Remove officer from assignment rotation")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        officerPoolRepository.findById(id).ifPresent(officer -> {
            officer.setActive(false);
            officerPoolRepository.save(officer);
        });
        return ResponseEntity.ok(ApiResponse.success(null, "Officer deactivated"));
    }

    @PostMapping("/release/{userId}")
    @Operation(summary = "Release workload", description = "Decrement workload when complaint is resolved/closed")
    public ResponseEntity<ApiResponse<Void>> release(@PathVariable String userId) {
        assignmentService.releaseAssignment(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Workload released"));
    }
}
