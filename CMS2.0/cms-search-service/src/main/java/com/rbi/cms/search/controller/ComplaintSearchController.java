package com.rbi.cms.search.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.search.config.CurrentOfficer;
import com.rbi.cms.search.dto.ComplaintSearchRequest;
import com.rbi.cms.search.dto.ComplaintSearchResponse;
import com.rbi.cms.search.dto.OfficerPrincipal;
import com.rbi.cms.search.service.ComplaintFilterSearchService;
import com.rbi.cms.search.service.ComplaintSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin(value = "http://localhost:4300")
@RequestMapping("/api/v1/search/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaint Filter Search", description = "Advanced complaint search with dynamic filters and aggregations")
public class ComplaintSearchController {

    private final ComplaintFilterSearchService filterSearchService;
    private final ComplaintSearchService searchService;

    @PostMapping("/search")
    @Operation(summary = "Search complaints with advanced filters, pagination, and KPI aggregations",
            description = "Accepts dynamic filters (advanced search, inline column search, KPI cards, tabs) via request body. "
                    + "Pagination via query params: ?page=0&size=10&sort=createdAt,desc. "
                    + "Returns paginated results with KPI card counts and tab counters.")
    public ResponseEntity<ApiResponse<ComplaintSearchResponse>> searchComplaints(
            @Valid @RequestBody ComplaintSearchRequest criteria,
            Pageable pageable,
            @CurrentOfficer OfficerPrincipal officer) {

        ComplaintSearchResponse result =
                filterSearchService.search(
                        criteria,
                        officer,
                        pageable);

        return ResponseEntity.ok(
                ApiResponse.success(result)
        );
    }

    @PostMapping("/reindex/allcomplaints")
    @Operation(summary = "Reindex all complaints", description = "Fetch all complaints from ingestion service and index them")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindexAllComplaints() {
        Map<String, Object> result = searchService.reindexAllComplaints();
        return ResponseEntity.ok(ApiResponse.success(result, "Reindex completed"));
    }

    @PostMapping("/reindex/allnodalofficers")
    @Operation(summary = "Reindex all Nodal Officer records", description = "Fetch all NO records from backend streaming service and index them into OpenSearch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindexAllNodalOfficers() {
        Map<String, Object> result = searchService.reindexAllNodalOfficers();
        return ResponseEntity.ok(ApiResponse.success(result, "Nodal Officer reindex completed"));
    }
}
