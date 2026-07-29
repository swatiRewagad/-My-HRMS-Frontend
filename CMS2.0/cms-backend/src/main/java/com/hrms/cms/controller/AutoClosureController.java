package com.hrms.cms.controller;

import com.hrms.cms.entity.AutoClosureQuestion;
import com.hrms.cms.service.AutoClosureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/crpc/auto-closure")
@RequiredArgsConstructor
public class AutoClosureController {

    private final AutoClosureService autoClosureService;

    @GetMapping("/questions")
    public ResponseEntity<List<AutoClosureQuestion>> getQuestions(
            @RequestParam(defaultValue = "RBIOS_2026") String schemeVersion,
            @RequestParam(defaultValue = "RBIO") String entityType) {
        return ResponseEntity.ok(autoClosureService.getQuestions(schemeVersion, entityType));
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
