package com.hrms.cms.controller;

import com.hrms.cms.dto.complaint.PastComplaintDetailResponse;
import com.hrms.cms.dto.complaint.PastComplaintSummary;
import com.hrms.cms.dto.complaint.SimilarCasesResponse;
import com.hrms.cms.service.PastComplaintService;
import com.rbi.cms.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Read-only history lookups an officer uses for context while working a live complaint.
 *
 * <p>The {@code count} key each endpoint used to publish alongside {@code data} is gone: no caller read
 * it, every screen sizes its own badge off the array it already has.
 */
@RestController
@RequestMapping("/api/v1/past-complaints")
@RequiredArgsConstructor
public class PastComplaintController {

    private final PastComplaintService pastComplaintService;

    @GetMapping("/by-complainant")
    public ResponseEntity<ApiResponse<List<PastComplaintSummary>>> getByComplainant(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String excludeId) {

        return ResponseEntity.ok(ApiResponse.success(
                pastComplaintService.findPastComplaints(email, phone, excludeId)));
    }

    @GetMapping("/detail/{complaintNumber}")
    public ResponseEntity<ApiResponse<PastComplaintDetailResponse>> getComplaintDetail(
            @PathVariable String complaintNumber) {

        PastComplaintDetailResponse detail = pastComplaintService.getComplaintDetail(complaintNumber);
        if (detail == null) {
            // Was a bodiless 404, so a caller could not tell a missing complaint from a routing miss.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No complaint found with number: " + complaintNumber));
        }
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PostMapping("/similar")
    public ResponseEntity<ApiResponse<SimilarCasesResponse>> findSimilar(
            @RequestBody Map<String, String> request) {

        String subject = request.getOrDefault("subject", "");
        String description = request.getOrDefault("description", "");
        String category = request.getOrDefault("category", "");
        String excludeId = request.getOrDefault("excludeId", "");

        return ResponseEntity.ok(ApiResponse.success(
                pastComplaintService.findSimilarCases(subject, description, category, excludeId)));
    }
}
