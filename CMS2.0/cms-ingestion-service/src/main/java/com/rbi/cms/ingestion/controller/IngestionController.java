package com.rbi.cms.ingestion.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.ingestion.dto.ComplaintDetailResponse;
import com.rbi.cms.ingestion.dto.ComplaintRegistrationRequest;
import com.rbi.cms.ingestion.dto.ComplaintRegistrationResponse;
import com.rbi.cms.ingestion.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaint Ingestion", description = "Complaint registration and attachment management")
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping
    @Operation(summary = "Register a new complaint", description = "Validates, persists, and triggers workflow for a new complaint")
    public ResponseEntity<ApiResponse<ComplaintRegistrationResponse>> register(
            @Valid @RequestBody ComplaintRegistrationRequest request) {

        ComplaintRegistrationResponse response = ingestionService.registerComplaint(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Complaint registered successfully"));
    }

    @PostMapping(value = "/{complaintId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload attachments", description = "Attach supporting documents to an existing complaint")
    public ResponseEntity<ApiResponse<Void>> uploadAttachments(
            @PathVariable String complaintId,
            @RequestParam("files") List<MultipartFile> files) {

        ingestionService.addAttachments(complaintId, files);
        return ResponseEntity.ok(ApiResponse.success(null, "Attachments uploaded successfully"));
    }

    @GetMapping("/{complaintId}")
    @Operation(summary = "Get complaint details", description = "Retrieve full complaint information including attachments and timeline")
    public ResponseEntity<ApiResponse<ComplaintDetailResponse>> getComplaint(
            @PathVariable String complaintId) {

        ComplaintDetailResponse response = ingestionService.getComplaint(complaintId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{complaintId}/status")
    @Operation(summary = "Update complaint status (PATCH)", description = "Transition complaint to a new status")
    public ResponseEntity<ApiResponse<Void>> updateStatusPatch(
            @PathVariable String complaintId,
            @RequestParam String status,
            @RequestParam(required = false) String remarks,
            @RequestParam(defaultValue = "OFFICER") String performedBy) {

        com.rbi.cms.common.enums.ComplaintStatus targetStatus =
                com.rbi.cms.common.enums.ComplaintStatus.valueOf(status);
        ingestionService.updateComplaintStatus(complaintId, targetStatus, remarks, performedBy);
        return ResponseEntity.ok(ApiResponse.success(null, "Status updated successfully"));
    }

    @PostMapping("/{complaintId}/status")
    @Operation(summary = "Update complaint status (POST)", description = "Transition complaint to a new status - used by internal services")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable String complaintId,
            @RequestParam String status,
            @RequestParam(required = false) String remarks,
            @RequestParam(defaultValue = "OFFICER") String performedBy) {

        com.rbi.cms.common.enums.ComplaintStatus targetStatus =
                com.rbi.cms.common.enums.ComplaintStatus.valueOf(status);
        ingestionService.updateComplaintStatus(complaintId, targetStatus, remarks, performedBy);
        return ResponseEntity.ok(ApiResponse.success(null, "Status updated successfully"));
    }

    @PatchMapping("/{complaintId}/assignment")
    @Operation(summary = "Update complaint assignment (PATCH)")
    public ResponseEntity<ApiResponse<Void>> updateAssignmentPatch(
            @PathVariable String complaintId,
            @RequestParam String team,
            @RequestParam(required = false) String officer) {

        ingestionService.updateAssignment(complaintId, team, officer);
        return ResponseEntity.ok(ApiResponse.success(null, "Assignment updated successfully"));
    }

    @PostMapping("/{complaintId}/assignment")
    @Operation(summary = "Update complaint assignment (POST)", description = "Used by internal services")
    public ResponseEntity<ApiResponse<Void>> updateAssignment(
            @PathVariable String complaintId,
            @RequestParam String team,
            @RequestParam(required = false) String officer) {

        ingestionService.updateAssignment(complaintId, team, officer);
        return ResponseEntity.ok(ApiResponse.success(null, "Assignment updated successfully"));
    }
}
