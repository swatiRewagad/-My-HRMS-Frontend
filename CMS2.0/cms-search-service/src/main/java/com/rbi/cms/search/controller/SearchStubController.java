package com.rbi.cms.search.controller;

import com.rbi.cms.common.dto.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
@ConditionalOnProperty(name = "cms.opensearch.enabled", havingValue = "false")
public class SearchStubController {

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<List<Map>>> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String team,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(List.of(), "OpenSearch disabled — stub response"));
    }

    @GetMapping("/complaints/status/{status}")
    public ResponseEntity<ApiResponse<List<Map>>> searchByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(List.of(), "OpenSearch disabled — stub response"));
    }

    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindex() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("indexed", 0, "message", "OpenSearch disabled — no-op"),
                "OpenSearch disabled — stub response"));
    }
}
