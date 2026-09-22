package com.hrms.cms.controller;

import com.hrms.cms.service.SimilarCasesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/similar-cases")

public class SimilarCasesController {

    private final SimilarCasesService similarCasesService;

    public SimilarCasesController(SimilarCasesService similarCasesService) {
        this.similarCasesService = similarCasesService;
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> findSimilar(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        String category = body.get("category");
        int maxResults = Integer.parseInt(body.getOrDefault("maxResults", "5"));

        List<Map<String, Object>> results = similarCasesService.findSimilar(text, category, maxResults);

        return ResponseEntity.ok(Map.of(
            "provider", similarCasesService.getActiveProvider(),
            "results", results,
            "count", results.size()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "provider", similarCasesService.getActiveProvider(),
            "available", !"none".equals(similarCasesService.getActiveProvider())
        ));
    }
}
