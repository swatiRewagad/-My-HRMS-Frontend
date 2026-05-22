package com.hrms.cms.controller;

import com.hrms.cms.dto.CategorizationResult;
import com.hrms.cms.dto.CategorizationRuleRequest;
import com.hrms.cms.entity.CategorizationRule;
import com.hrms.cms.service.CategorizationRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categorization-rules")
@RequiredArgsConstructor
public class CategorizationRuleController {

    private final CategorizationRuleService ruleService;

    @GetMapping
    public List<CategorizationRule> getAll() {
        return ruleService.getAllRules();
    }

    @GetMapping("/active")
    public List<CategorizationRule> getActive() {
        return ruleService.getActiveRules();
    }

    @GetMapping("/{id}")
    public CategorizationRule getById(@PathVariable Long id) {
        return ruleService.getRule(id);
    }

    @PostMapping
    public CategorizationRule create(@RequestBody CategorizationRuleRequest req) {
        return ruleService.createRule(req);
    }

    @PutMapping("/{id}")
    public CategorizationRule update(@PathVariable Long id, @RequestBody CategorizationRuleRequest req) {
        return ruleService.updateRule(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/categorize")
    public List<CategorizationResult> categorize(@RequestBody Map<String, String> request) {
        String text = request.getOrDefault("text", "");
        String source = request.getOrDefault("source", "all");
        return ruleService.categorize(text, source);
    }
}
