package com.rbi.cms.workflow.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.workflow.entity.OfficerPool;
import com.rbi.cms.workflow.repository.OfficerPoolRepository;
import com.rbi.cms.workflow.service.OfficerDeactivationService;
import com.rbi.cms.workflow.service.RoundRobinAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assignment")
@RequiredArgsConstructor
@Tag(name = "Assignment", description = "Officer pool and round-robin assignment management")
public class AssignmentController {

    private final OfficerPoolRepository officerPoolRepository;
    private final RoundRobinAssignmentService assignmentService;
    private final OfficerDeactivationService deactivationService;

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

    @GetMapping("/pool/{id}/open-records")
    @Operation(summary = "Open records held by an officer",
            description = "Shows what must be reassigned before this officer can be deactivated")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openRecords(@PathVariable Long id) {
        return officerPoolRepository.findById(id)
                .map(officer -> {
                    List<String> open = deactivationService.findOpenComplaints(officer.getUserId());
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("userId", officer.getUserId());
                    body.put("displayName", officer.getDisplayName());
                    body.put("roleGroup", officer.getRoleGroup());
                    body.put("openCount", open.size());
                    body.put("openComplaints", open);
                    body.put("reassignmentRequired", !open.isEmpty());
                    return ResponseEntity.ok(ApiResponse.success(body));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Officer not found")));
    }

    /**
     * UST887: safe deactivation.
     *
     * This used to flip the active flag and report success, leaving any complaints the officer still
     * held assigned to someone who could no longer act on them. Open records must now be transferred
     * in the same transaction, so deactivation cannot strand work.
     */
    @PutMapping("/pool/{id}/deactivate")
    @Operation(summary = "Deactivate officer",
            description = "Reassigns still-open records, then removes the officer from rotation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivate(
            @PathVariable Long id,
            @RequestParam(required = false) String reassignTo,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String actor) {

        String successor = reassignTo != null ? reassignTo : (body == null ? null : body.get("reassignTo"));

        try {
            OfficerDeactivationService.DeactivationResult result =
                    deactivationService.deactivate(id, successor, actor == null ? "SYSTEM" : actor);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", result.userId());
            payload.put("displayName", result.displayName());
            payload.put("reassignedCount", result.reassignedCount());
            payload.put("reassignedComplaints", result.reassignedComplaints());
            payload.put("reassignedTo", result.reassignedTo());

            return ResponseEntity.ok(ApiResponse.success(payload,
                    result.reassignedCount() == 0
                            ? "Officer deactivated"
                            : "Officer deactivated; " + result.reassignedCount()
                              + " open complaint(s) reassigned to " + result.reassignedTo()));

        } catch (OfficerDeactivationService.OfficerNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));

        } catch (OfficerDeactivationService.ReassignmentRequiredException e) {
            // 409: the request was well-formed but cannot complete until a successor is named.
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reassignmentRequired", true);
            payload.put("openComplaints", e.getOpenComplaints());
            payload.put("openCount", e.getOpenComplaints().size());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .success(false)
                            .message(e.getMessage())
                            .data(payload)
                            .build());

        } catch (OfficerDeactivationService.InvalidSuccessorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/release/{userId}")
    @Operation(summary = "Release workload", description = "Decrement workload when complaint is resolved/closed")
    public ResponseEntity<ApiResponse<Void>> release(@PathVariable String userId) {
        assignmentService.releaseAssignment(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Workload released"));
    }
}
