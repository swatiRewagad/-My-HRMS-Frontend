package com.rbi.cms.ingestion.email.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.ingestion.email.dto.IgnoreListRequest;
import com.rbi.cms.ingestion.email.dto.IgnoreListResponse;
import com.rbi.cms.ingestion.email.service.IgnoreListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/email-syndication/ignore-list")
@RequiredArgsConstructor
@Tag(name = "Email Ignore List", description = "Manage email addresses/domains to exclude from auto-ingestion")
public class IgnoreListController {

    private final IgnoreListService ignoreListService;

    @GetMapping
    @Operation(summary = "Get all ignore list entries")
    public ResponseEntity<ApiResponse<List<IgnoreListResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(ignoreListService.getAll(), "Ignore list fetched"));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active ignore list entries only")
    public ResponseEntity<ApiResponse<List<IgnoreListResponse>>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(ignoreListService.getActive(), "Active entries fetched"));
    }

    @PostMapping
    @Operation(summary = "Add entry to ignore list", description = "Supports exact email, domain (*@example.com), or wildcard patterns")
    public ResponseEntity<ApiResponse<IgnoreListResponse>> add(
            @Valid @RequestBody IgnoreListRequest request,
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        IgnoreListResponse response = ignoreListService.add(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Added to ignore list"));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk add entries to ignore list (CSV upload support)")
    public ResponseEntity<ApiResponse<List<IgnoreListResponse>>> bulkAdd(
            @RequestBody List<IgnoreListRequest> requests,
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        List<IgnoreListResponse> responses = ignoreListService.bulkAdd(requests, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(responses, responses.size() + " entries added"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ignore list entry")
    public ResponseEntity<ApiResponse<IgnoreListResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody IgnoreListRequest request,
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        IgnoreListResponse response = ignoreListService.update(id, request, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Entry updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove entry from ignore list (soft delete)")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long id) {
        ignoreListService.remove(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Entry removed"));
    }
}
