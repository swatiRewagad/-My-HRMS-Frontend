package com.rbi.cms.search.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.search.reindex.ReindexJob;
import com.rbi.cms.search.service.ComplaintSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Full-text complaint search via Elasticsearch")
public class SearchController {

    private final ComplaintSearchService searchService;
    private final ReindexJob reindexJob;

    @GetMapping("/complaints")
    @Operation(summary = "Search complaints",
            description = "Full text search across complaint, timeline and appeal-order text in all supported languages")
    public ResponseEntity<ApiResponse<ComplaintSearchService.SearchPage>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String entityCode,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String workflowStage,
            @RequestParam(required = false) String milestone,
            @RequestParam(required = false) String rbioOfficeCode,
            @RequestParam(required = false) String groundOfComplaintId,
            @RequestParam(required = false) String compensationType,
            @RequestParam(required = false) String maintainabilityDetermination,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var query = new ComplaintSearchService.SearchQuery(q, categoryId, status, entityCode, department,
                workflowStage, milestone, rbioOfficeCode, groundOfComplaintId, compensationType,
                maintainabilityDetermination, priority, language, page, size);

        return ResponseEntity.ok(ApiResponse.success(searchService.search(query)));
    }

    @GetMapping("/complaints/status/{status}")
    @Operation(summary = "Search by status")
    public ResponseEntity<ApiResponse<ComplaintSearchService.SearchPage>> searchByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var query = new ComplaintSearchService.SearchQuery(null, null, status, null, null, null, null,
                null, null, null, null, null, null, page, size);

        return ResponseEntity.ok(ApiResponse.success(searchService.search(query)));
    }

    /**
     * Synchronous on purpose. A reindex is an operator action run deliberately, and an operator who
     * triggers it needs the outcome, not a job id to poll. The rate limiting that makes it safe
     * during business hours is inside the job (batch size + inter-batch pause), not in the transport.
     */
    @PostMapping("/reindex")
    @Operation(summary = "Rebuild the complaint index",
            description = "Keyset-paged bulk rebuild into a new index, followed by an atomic alias swap. "
                    + "Safe to interrupt: the live alias is not moved until the rebuild completes.")
    public ResponseEntity<ApiResponse<ReindexJob.ReindexStatus>> reindex() {
        ReindexJob.ReindexStatus status = reindexJob.run();
        return ResponseEntity.ok(ApiResponse.success(status, status.message()));
    }

    @GetMapping("/reindex/status")
    @Operation(summary = "Outcome of the last reindex in this process")
    public ResponseEntity<ApiResponse<ReindexJob.ReindexStatus>> reindexStatus() {
        return ResponseEntity.ok(ApiResponse.success(reindexJob.getLastStatus()));
    }
}
