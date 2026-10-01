package com.hrms.cms.controller;

import com.hrms.cms.entity.AutoClosureQuestion;
import com.hrms.cms.service.AutoClosureService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/crpc/auto-closure")
@RequiredArgsConstructor
public class AutoClosureController {

    private final AutoClosureService autoClosureService;

    /**
     * Scheme applied when the caller does not name one.
     *
     * {@code @RequestParam(defaultValue = ...)} cannot express this: an annotation attribute must be a
     * compile-time constant, so it can only ever hold a literal — and it held "RBIOS_2026", selecting
     * the question set for a Scheme that is not in force. The parameter is optional instead and the
     * default comes from cms.eligibility.scheme-version.
     */
    @Value("${cms.eligibility.scheme-version:RBIOS_2021}")
    private String defaultSchemeVersion;

    @GetMapping("/questions")
    public ResponseEntity<List<AutoClosureQuestion>> getQuestions(
            @RequestParam(required = false) String schemeVersion,
            @RequestParam(defaultValue = "RBIO") String entityType) {
        return ResponseEntity.ok(
                autoClosureService.getQuestions(resolveScheme(schemeVersion), entityType));
    }

    private String resolveScheme(String requested) {
        return (requested == null || requested.isBlank()) ? defaultSchemeVersion : requested;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateResponse(
            @RequestParam String schemeVersion,
            @RequestParam String entityType,
            @RequestParam int questionNumber,
            @RequestParam String answer) {
        return ResponseEntity.ok(autoClosureService.evaluateResponse(schemeVersion, entityType, questionNumber, answer));
    }

    @PostMapping("/evaluate-all")
    public ResponseEntity<Map<String, Object>> evaluateAllResponses(
            @RequestParam String schemeVersion,
            @RequestParam String entityType,
            @RequestBody List<Map<String, String>> responses) {
        return ResponseEntity.ok(autoClosureService.evaluateAllResponses(schemeVersion, entityType, responses));
    }
}
