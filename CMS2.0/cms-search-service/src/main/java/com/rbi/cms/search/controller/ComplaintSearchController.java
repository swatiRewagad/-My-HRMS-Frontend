package com.rbi.cms.search.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.common.dto.PagedResponse;
import com.rbi.cms.search.dto.ComplaintListResponse;
import com.rbi.cms.search.dto.ComplaintSearchRequestDTO;
import com.rbi.cms.search.service.ComplaintFilterSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaint Filter Search", description = "Advanced complaint search with dynamic filters")
public class ComplaintSearchController {

    private final ComplaintFilterSearchService filterSearchService;

    @PostMapping("/search")
    @Operation(summary = "Search complaints with advanced filters and pagination",
            description = "Accepts dynamic filters (advanced search, inline column search, KPI cards, tabs) and returns paginated results")
    public ResponseEntity<ApiResponse<PagedResponse<ComplaintListResponse>>> searchComplaints(
            @RequestBody ComplaintSearchRequestDTO request,
            @RequestHeader(value = "X-Current-Officer", required = false) String currentOfficer) {

        PagedResponse<ComplaintListResponse> result = filterSearchService.search(request, currentOfficer);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
