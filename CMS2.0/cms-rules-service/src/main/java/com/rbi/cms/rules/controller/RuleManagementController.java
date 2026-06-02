package com.rbi.cms.rules.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.rules.dto.*;
import com.rbi.cms.rules.entity.RuleDeploymentLog;
import com.rbi.cms.rules.entity.RuleStatus;
import com.rbi.cms.rules.service.RuleManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
@Tag(name = "Rules Management", description = "CRUD operations for business rules with versioning and hot-reload")
public class RuleManagementController {

    private final RuleManagementService ruleService;

    @GetMapping
    @Operation(summary = "List all rules", description = "Get all rules with optional category and status filters")
    public ResponseEntity<ApiResponse<List<RuleResponse>>> getAllRules(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) RuleStatus status) {

        List<RuleResponse> rules;
        if (category != null) {
            rules = ruleService.getRulesByCategory(category);
        } else if (status != null) {
            rules = ruleService.getRulesByStatus(status);
        } else {
            rules = ruleService.getAllRules();
        }
        return ResponseEntity.ok(ApiResponse.success(rules, "Rules fetched successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get rule by ID")
    public ResponseEntity<ApiResponse<RuleResponse>> getRule(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(ruleService.getRule(id), "Rule fetched"));
    }

    @GetMapping("/code/{ruleCode}")
    @Operation(summary = "Get rule by code")
    public ResponseEntity<ApiResponse<RuleResponse>> getRuleByCode(@PathVariable String ruleCode) {
        return ResponseEntity.ok(ApiResponse.success(ruleService.getRuleByCode(ruleCode), "Rule fetched"));
    }

    @PostMapping
    @Operation(summary = "Create a new rule", description = "Creates a rule in DRAFT status")
    public ResponseEntity<ApiResponse<RuleResponse>> createRule(
            @Valid @RequestBody RuleRequest request,
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        RuleResponse response = ruleService.createRule(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Rule created successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing rule", description = "Increments version and resets to DRAFT")
    public ResponseEntity<ApiResponse<RuleResponse>> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody RuleRequest request,
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        RuleResponse response = ruleService.updateRule(id, request, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Rule updated successfully"));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a rule (Checker approval)", description = "Maker-Checker: approver must be different from creator")
    public ResponseEntity<ApiResponse<RuleResponse>> activateRule(
            @PathVariable Long id,
            @RequestHeader(value = "X-User", defaultValue = "system") String approver) {

        RuleResponse response = ruleService.activateRule(id, approver);
        return ResponseEntity.ok(ApiResponse.success(response, "Rule activated successfully"));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a rule")
    public ResponseEntity<ApiResponse<RuleResponse>> deactivateRule(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Manual deactivation") String reason,
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        RuleResponse response = ruleService.deactivateRule(id, user, reason);
        return ResponseEntity.ok(ApiResponse.success(response, "Rule deactivated"));
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "Rollback to previous version")
    public ResponseEntity<ApiResponse<RuleResponse>> rollbackRule(
            @PathVariable Long id,
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        RuleResponse response = ruleService.rollbackRule(id, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Rule rolled back successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive a rule (soft delete)")
    public ResponseEntity<ApiResponse<Void>> archiveRule(
            @PathVariable Long id,
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        ruleService.archiveRule(id, user);
        return ResponseEntity.ok(ApiResponse.success(null, "Rule archived"));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get version history of a rule")
    public ResponseEntity<ApiResponse<List<RuleHistoryResponse>>> getRuleHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(ruleService.getRuleHistory(id), "History fetched"));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate DRL syntax", description = "Check if DRL content compiles without errors")
    public ResponseEntity<ApiResponse<RuleValidationResponse>> validateDrl(
            @RequestBody Map<String, String> body) {

        String drlContent = body.get("drlContent");
        if (drlContent == null || drlContent.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("drlContent is required"));
        }

        RuleValidationResponse response = ruleService.validateDrl(drlContent);
        return ResponseEntity.ok(ApiResponse.success(response,
                response.isValid() ? "DRL is valid" : "DRL has errors"));
    }

    @PostMapping("/test")
    @Operation(summary = "Test rules in sandbox", description = "Execute rules against test input without affecting live data")
    public ResponseEntity<ApiResponse<RuleTestResponse>> testRules(
            @Valid @RequestBody RuleTestRequest request) {

        RuleTestResponse response = ruleService.testRule(request);
        return ResponseEntity.ok(ApiResponse.success(response,
                response.isExecuted() ? "Test executed" : "Test failed"));
    }

    @PostMapping("/deploy")
    @Operation(summary = "Deploy active rules", description = "Hot-reload all active rules into the engine")
    public ResponseEntity<ApiResponse<DeploymentResponse>> deployRules(
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        DeploymentResponse response = ruleService.deployRules(user);
        return ResponseEntity.ok(ApiResponse.success(response,
                "SUCCESS".equals(response.getStatus()) ? "Rules deployed successfully" : "Deployment failed"));
    }

    @GetMapping("/deployments")
    @Operation(summary = "Get deployment history")
    public ResponseEntity<ApiResponse<List<RuleDeploymentLog>>> getDeploymentHistory() {
        return ResponseEntity.ok(ApiResponse.success(ruleService.getDeploymentHistory(), "Deployment history"));
    }
}
