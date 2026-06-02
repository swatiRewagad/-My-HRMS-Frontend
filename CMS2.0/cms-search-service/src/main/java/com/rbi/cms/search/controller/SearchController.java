package com.rbi.cms.search.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.search.service.ComplaintSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Full-text complaint search via OpenSearch")
public class SearchController {

    private final ComplaintSearchService searchService;

    @GetMapping("/complaints")
    @Operation(summary = "Search complaints", description = "Full text search across complaint fields")
    public ResponseEntity<ApiResponse<List<Map>>> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String team,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws IOException {

        List<Map> results = searchService.search(q, category, status, priority, team, page, size);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @GetMapping("/complaints/status/{status}")
    @Operation(summary = "Search by status", description = "Find complaints by workflow status")
    public ResponseEntity<ApiResponse<List<Map>>> searchByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws IOException {

        List<Map> results = searchService.searchByStatus(status, page, size);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PostMapping("/reindex")
    @Operation(summary = "Reindex all complaints", description = "Fetch all complaints from ingestion service and index them")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindex() {
        Map<String, Object> result = searchService.reindexAll();
        return ResponseEntity.ok(ApiResponse.success(result, "Reindex completed"));
    }
}
