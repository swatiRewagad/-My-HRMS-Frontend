package com.rbi.cms.ingestion.email.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.ingestion.email.dto.*;
import com.rbi.cms.ingestion.email.service.EmailSyndicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/email-syndication")
@RequiredArgsConstructor
@Tag(name = "Email Syndication", description = "Email ingestion, draft management, and DEO assignment")
public class EmailSyndicationController {

    private final EmailSyndicationService emailService;

    @PostMapping("/ingest")
    @Operation(summary = "Ingest an incoming email", description = "Creates a draft complaint from incoming email to crpc@rbi.org.in")
    public ResponseEntity<ApiResponse<EmailDraftResponse>> ingestEmail(
            @Valid @RequestBody EmailIngestRequest request) {

        EmailDraftResponse response = emailService.ingestEmail(request);
        if (response == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "Email ignored (sender on ignore list)"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Email ingested successfully"));
    }

    @GetMapping("/queue")
    @Operation(summary = "Get email draft queue", description = "Lists email drafts filtered by status")
    public ResponseEntity<ApiResponse<List<EmailDraftResponse>>> getQueue(
            @RequestParam(required = false) String status) {

        List<EmailDraftResponse> queue = emailService.getEmailQueue(status);
        return ResponseEntity.ok(ApiResponse.success(queue, "Queue fetched"));
    }

    @GetMapping("/drafts/{draftId}")
    @Operation(summary = "Get draft details", description = "Get full draft details with attachments and suggested related complaints")
    public ResponseEntity<ApiResponse<EmailDraftResponse>> getDraft(@PathVariable String draftId) {
        EmailDraftResponse response = emailService.getDraft(draftId);
        return ResponseEntity.ok(ApiResponse.success(response, "Draft fetched"));
    }

    @PutMapping("/drafts/{draftId}")
    @Operation(summary = "Update draft fields", description = "DEO updates OCR-prefilled or manually entered fields")
    public ResponseEntity<ApiResponse<EmailDraftResponse>> updateDraft(
            @PathVariable String draftId,
            @RequestBody EmailDraftUpdateRequest request) {

        EmailDraftResponse response = emailService.updateDraft(draftId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Draft updated"));
    }

    @PostMapping("/drafts/{draftId}/convert")
    @Operation(summary = "Convert draft to complaint", description = "Converts a processed email draft into a formal complaint")
    public ResponseEntity<ApiResponse<EmailDraftResponse>> convertToComplaint(
            @PathVariable String draftId,
            @RequestHeader(value = "X-User", defaultValue = "system") String user) {

        EmailDraftResponse response = emailService.convertToComplaint(draftId, user);
        return ResponseEntity.ok(ApiResponse.success(response, "Draft converted to complaint"));
    }

    @PostMapping("/drafts/{draftId}/reassign")
    @Operation(summary = "Reassign draft to another DEO")
    public ResponseEntity<ApiResponse<Void>> reassignDraft(
            @PathVariable String draftId,
            @RequestParam String targetDeoId,
            @RequestHeader(value = "X-User", defaultValue = "system") String requestedBy) {

        emailService.reassignDraft(draftId, targetDeoId, requestedBy);
        return ResponseEntity.ok(ApiResponse.success(null, "Draft reassigned"));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get email queue statistics")
    public ResponseEntity<ApiResponse<EmailQueueStats>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(emailService.getStats(), "Stats fetched"));
    }
}
