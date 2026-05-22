package com.hrms.cms.service;

import com.hrms.cms.dto.CategorizationResult;
import com.hrms.cms.dto.CategorizationRuleRequest;
import com.hrms.cms.entity.CategorizationRule;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.repository.CategorizationRuleRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategorizationRuleService {

    private final CategorizationRuleRepository ruleRepository;
    private final ComplaintCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategorizationRule> getAllRules() {
        return ruleRepository.findAllByOrderByRuleOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<CategorizationRule> getActiveRules() {
        return ruleRepository.findByStatusOrderByRuleOrderAsc("active");
    }

    @Transactional(readOnly = true)
    public CategorizationRule getRule(Long id) {
        return ruleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Rule not found: " + id));
    }

    @Transactional
    public CategorizationRule createRule(CategorizationRuleRequest req) {
        CategorizationRule rule = CategorizationRule.builder()
            .ruleName(req.getRuleName())
            .keywords(req.getKeywords())
            .categoryId(req.getCategoryId())
            .priority(req.getPriority())
            .status(req.getStatus() != null ? req.getStatus() : "active")
            .source(req.getSource())
            .description(req.getDescription())
            .ruleOrder(req.getRuleOrder() != null ? req.getRuleOrder() : 0)
            .build();
        return ruleRepository.save(rule);
    }

    @Transactional
    public CategorizationRule updateRule(Long id, CategorizationRuleRequest req) {
        CategorizationRule rule = getRule(id);
        if (req.getRuleName() != null) rule.setRuleName(req.getRuleName());
        if (req.getKeywords() != null) rule.setKeywords(req.getKeywords());
        if (req.getCategoryId() != null) rule.setCategoryId(req.getCategoryId());
        if (req.getPriority() != null) rule.setPriority(req.getPriority());
        if (req.getStatus() != null) rule.setStatus(req.getStatus());
        if (req.getSource() != null) rule.setSource(req.getSource());
        if (req.getDescription() != null) rule.setDescription(req.getDescription());
        if (req.getRuleOrder() != null) rule.setRuleOrder(req.getRuleOrder());
        return ruleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CategorizationResult> categorize(String complaintText, String source) {
        if (complaintText == null || complaintText.isBlank()) {
            return Collections.emptyList();
        }

        String normalizedText = complaintText.toLowerCase().trim();
        List<CategorizationRule> rules = ruleRepository.findByStatusOrderByRuleOrderAsc("active");

        if (source != null && !source.isBlank()) {
            List<CategorizationRule> sourceRules = rules.stream()
                .filter(r -> r.getSource() == null || r.getSource().isBlank()
                    || r.getSource().equalsIgnoreCase(source) || "all".equalsIgnoreCase(r.getSource()))
                .collect(Collectors.toList());
            rules = sourceRules;
        }

        Map<Long, CategorizationResult> results = new LinkedHashMap<>();

        for (CategorizationRule rule : rules) {
            String[] keywordArray = rule.getKeywords().split(",");
            List<String> matched = new ArrayList<>();

            for (String keyword : keywordArray) {
                String kw = keyword.trim().toLowerCase();
                if (!kw.isEmpty() && normalizedText.contains(kw)) {
                    matched.add(keyword.trim());
                }
            }

            if (!matched.isEmpty()) {
                double confidence = Math.min(100.0, (matched.size() * 100.0) / keywordArray.length);
                Long catId = rule.getCategoryId();

                if (!results.containsKey(catId) || results.get(catId).getConfidence() < confidence) {
                    String categoryName = categoryRepository.findById(catId)
                        .map(ComplaintCategory::getName)
                        .orElse("Unknown");

                    results.put(catId, CategorizationResult.builder()
                        .categoryId(catId)
                        .categoryName(categoryName)
                        .matchedRule(rule.getRuleName())
                        .matchedKeywords(matched)
                        .priority(rule.getPriority())
                        .confidence(Math.round(confidence * 10.0) / 10.0)
                        .build());
                }
            }
        }

        return results.values().stream()
            .sorted(Comparator.comparingDouble(CategorizationResult::getConfidence).reversed())
            .collect(Collectors.toList());
    }
}
