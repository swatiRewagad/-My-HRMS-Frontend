package com.rbi.cms.assignment.web;

import com.rbi.cms.assignment.service.RuleSetCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/assignment/internal")
@RequiredArgsConstructor
public class InternalController {

    private final RuleSetCacheService cacheService;

    @PostMapping("/rules/cache/refresh")
    public ResponseEntity<Map<String, String>> refreshCache(
            @RequestParam(required = false) String decisionPoint) {
        if (decisionPoint != null) {
            cacheService.refresh(decisionPoint);
        } else {
            cacheService.refreshAll();
        }
        return ResponseEntity.ok(Map.of("status", "refreshed"));
    }
}
