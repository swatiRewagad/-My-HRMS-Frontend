package com.hrms.cms.controller;

import com.hrms.cms.dto.*;
import com.hrms.cms.service.ExtractionRuleService;
import com.hrms.cms.service.RuleBasedExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/extraction-rules")

@RequiredArgsConstructor
public class ExtractionRuleController {

    private final ExtractionRuleService ruleService;
    private final RuleBasedExtractor extractor;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllRules(
            @RequestParam(required = false) Boolean active) {
        List<ExtractionRuleResponse> rules = ruleService.getAllRules(active);
        return ResponseEntity.ok(Map.of("data", rules, "total", rules.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getRule(@PathVariable Long id) {
        ExtractionRuleResponse rule = ruleService.getRule(id);
        return ResponseEntity.ok(Map.of("data", rule));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRule(
            @Valid @RequestBody ExtractionRuleRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "admin") String userId) {
        ExtractionRuleResponse rule = ruleService.createRule(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody ExtractionRuleRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "admin") String userId) {
        ExtractionRuleResponse rule = ruleService.updateRule(id, request, userId);
        return ResponseEntity.ok(Map.of("data", rule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleRule(@PathVariable Long id) {
        ExtractionRuleResponse rule = ruleService.toggleRule(id);
        return ResponseEntity.ok(Map.of("data", rule));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testRules(@RequestBody ExtractionTestRequest request) {
        ExtractionTestResponse result = extractor.testExtraction(request.getSubject(), request.getBody());
        return ResponseEntity.ok(Map.of("data", result));
    }

    @GetMapping("/target-fields")
    public ResponseEntity<Map<String, Object>> getTargetFields() {
        Set<String> fields = extractor.getAllTargetFields();
        List<String> sorted = new ArrayList<>(fields);
        Collections.sort(sorted);
        return ResponseEntity.ok(Map.of("data", sorted));
    }
}
