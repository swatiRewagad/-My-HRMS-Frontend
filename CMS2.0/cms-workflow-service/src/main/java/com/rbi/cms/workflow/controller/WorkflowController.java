package com.rbi.cms.workflow.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.workflow.dto.OfficerTaskResponse;
import com.rbi.cms.workflow.service.ComplaintWorkflowProcessor;
import com.rbi.cms.workflow.service.TaskQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@Tag(name = "Workflow", description = "Complaint lifecycle workflow management")
public class WorkflowController {

    private final ComplaintWorkflowProcessor workflowService;
    private final TaskQueryService taskQueryService;

    @GetMapping("/tasks")
    @Operation(summary = "Get assigned tasks", description = "Retrieve complaints assigned to a team/officer")
    public ResponseEntity<ApiResponse<List<OfficerTaskResponse>>> getTasks(
            @RequestParam String team,
            @RequestParam(required = false) String status) {

        List<OfficerTaskResponse> tasks = taskQueryService.getTasksForTeam(team, status);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @PostMapping("/{complaintId}/transition")
    @Operation(summary = "Transition complaint state", description = "Move complaint to next workflow state")
    public ResponseEntity<ApiResponse<Void>> transitionState(
            @PathVariable String complaintId,
            @RequestParam ComplaintStatus targetStatus,
            @RequestParam(required = false) String remarks) {

        workflowService.transitionState(complaintId, targetStatus, remarks);
        return ResponseEntity.ok(ApiResponse.success(null, "State transitioned successfully"));
    }

    @PostMapping("/{complaintId}/escalate")
    @Operation(summary = "Escalate complaint", description = "Escalate a complaint due to SLA breach or priority")
    public ResponseEntity<ApiResponse<Void>> escalate(
            @PathVariable String complaintId,
            @RequestParam String reason) {

        workflowService.escalateComplaint(complaintId, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Complaint escalated successfully"));
    }
}
