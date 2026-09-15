package com.rbi.cms.portal.api;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.portal.dto.request.ComplaintSubmitRequestDto;
import com.rbi.cms.portal.dto.response.ComplaintAcknowledgementDto;
import com.rbi.cms.portal.dto.response.ComplaintStatusDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Complaint", description = "Complaint registration and tracking APIs consumed by the portal")
@RequestMapping("/api/v1/complaints")
public interface ComplaintApi {

    @PostMapping
    @Operation(summary = "Register complaint", description = "Submit a new complaint after eligibility is confirmed")
    ResponseEntity<ApiResponse<ComplaintAcknowledgementDto>> submitComplaint(
            @Valid @RequestBody ComplaintSubmitRequestDto request);

    @PostMapping("/{complaintId}/attachments")
    @Operation(summary = "Upload attachments", description = "Attach evidence documents to a complaint")
    ResponseEntity<ApiResponse<Void>> uploadAttachments(
            @PathVariable String complaintId,
            @RequestParam("files") List<MultipartFile> files);

    @GetMapping("/{complaintId}/status")
    @Operation(summary = "Track complaint status", description = "Public endpoint to check complaint progress")
    ResponseEntity<ApiResponse<ComplaintStatusDto>> trackStatus(
            @PathVariable String complaintId);
}
