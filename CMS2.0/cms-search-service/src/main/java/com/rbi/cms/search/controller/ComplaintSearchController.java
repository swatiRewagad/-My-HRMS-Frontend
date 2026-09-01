package com.rbi.cms.search.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.search.dto.ComplaintSearchRequestDTO;
import com.rbi.cms.search.dto.ComplaintSearchResponse;
import com.rbi.cms.search.dto.OfficerContext;
import com.rbi.cms.search.service.ComplaintFilterSearchService;
import com.rbi.cms.search.service.OfficerContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaint Filter Search", description = "Advanced complaint search with dynamic filters and aggregations")
public class ComplaintSearchController {

    private final ComplaintFilterSearchService filterSearchService;
    private final OfficerContextService officerContextService;

    @PostMapping("/search")
    @Operation(summary = "Search complaints with advanced filters, pagination, and KPI aggregations",
            description = "Accepts dynamic filters (advanced search, inline column search, KPI cards, tabs) via request body. "
                    + "Pagination via query params: ?page=0&size=10&sort=createdAt,desc. "
                    + "Returns paginated results with KPI card counts and tab counters.")
    public ResponseEntity<ApiResponse<ComplaintSearchResponse>> searchComplaints(
            @RequestBody ComplaintSearchRequestDTO criteria,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader(value = "X-Current-Officer", required = false) String currentOfficer) {

        OfficerContext officer = officerContextService.getOfficerContext(currentOfficer);
        ComplaintSearchResponse result = filterSearchService.search(criteria, officer, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
