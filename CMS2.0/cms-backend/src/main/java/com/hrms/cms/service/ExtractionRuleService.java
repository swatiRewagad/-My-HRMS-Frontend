package com.hrms.cms.service;

import com.hrms.cms.dto.ExtractionRuleRequest;
import com.hrms.cms.dto.ExtractionRuleResponse;
import com.hrms.cms.entity.ExtractionRule;
import com.hrms.cms.repository.ExtractionRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExtractionRuleService {

    private final ExtractionRuleRepository repository;

    public List<ExtractionRuleResponse> getAllRules(Boolean activeOnly) {
        List<ExtractionRule> rules;
        if (Boolean.TRUE.equals(activeOnly)) {
            rules = repository.findByIsActiveTrueOrderByPriorityAsc();
        } else {
            rules = repository.findAllByOrderByPriorityAsc();
        }
        return rules.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ExtractionRuleResponse getRule(Long id) {
        ExtractionRule rule = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Extraction rule not found: " + id));
        return toResponse(rule);
    }

    @Transactional
    public ExtractionRuleResponse createRule(ExtractionRuleRequest request, String createdBy) {
        validatePattern(request);

        String ruleCode = request.getRuleCode();
        if (ruleCode == null || ruleCode.isBlank()) {
            ruleCode = generateRuleCode(request.getRuleName());
        }

        if (repository.existsByRuleCode(ruleCode)) {
            throw new RuntimeException("Rule code already exists: " + ruleCode);
        }

        ExtractionRule rule = ExtractionRule.builder()
                .ruleName(request.getRuleName())
                .ruleCode(ruleCode)
                .description(request.getDescription())
                .patternType(request.getPatternType())
                .pattern(request.getPattern())
                .targetField(request.getTargetField())
                .extractGroup(request.getExtractGroup() != null ? request.getExtractGroup() : 0)
                .transform(request.getTransform())
                .priority(request.getPriority())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .sourceScope(request.getSourceScope() != null ? request.getSourceScope() : "BOTH")
                .createdBy(createdBy)
                .build();

        rule = repository.save(rule);
        log.info("Created extraction rule: {} ({})", rule.getRuleName(), rule.getRuleCode());
        return toResponse(rule);
    }

    @Transactional
    public ExtractionRuleResponse updateRule(Long id, ExtractionRuleRequest request, String updatedBy) {
        ExtractionRule rule = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Extraction rule not found: " + id));

        validatePattern(request);

        rule.setRuleName(request.getRuleName());
        rule.setDescription(request.getDescription());
        rule.setPatternType(request.getPatternType());
        rule.setPattern(request.getPattern());
        rule.setTargetField(request.getTargetField());
        rule.setExtractGroup(request.getExtractGroup() != null ? request.getExtractGroup() : 0);
        rule.setTransform(request.getTransform());
        rule.setPriority(request.getPriority());
        rule.setSourceScope(request.getSourceScope() != null ? request.getSourceScope() : "BOTH");
        rule.setUpdatedBy(updatedBy);

        if (request.getIsActive() != null) {
            rule.setIsActive(request.getIsActive());
        }

        rule = repository.save(rule);
        log.info("Updated extraction rule: {} ({})", rule.getRuleName(), rule.getRuleCode());
        return toResponse(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        ExtractionRule rule = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Extraction rule not found: " + id));
        repository.delete(rule);
        log.info("Deleted extraction rule: {} ({})", rule.getRuleName(), rule.getRuleCode());
    }

    @Transactional
    public ExtractionRuleResponse toggleRule(Long id) {
        ExtractionRule rule = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Extraction rule not found: " + id));
        rule.setIsActive(!rule.getIsActive());
        rule = repository.save(rule);
        log.info("Toggled extraction rule '{}' active={}", rule.getRuleCode(), rule.getIsActive());
        return toResponse(rule);
    }

    private void validatePattern(ExtractionRuleRequest request) {
        if ("REGEX".equals(request.getPatternType())) {
            try {
                Pattern.compile(request.getPattern());
            } catch (Exception e) {
                throw new RuntimeException("Invalid regex pattern: " + e.getMessage());
            }
        }
    }

    private String generateRuleCode(String ruleName) {
        return ruleName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "")
                .substring(0, Math.min(ruleName.length(), 80));
    }

    private ExtractionRuleResponse toResponse(ExtractionRule rule) {
        return ExtractionRuleResponse.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .ruleCode(rule.getRuleCode())
                .description(rule.getDescription())
                .patternType(rule.getPatternType())
                .pattern(rule.getPattern())
                .targetField(rule.getTargetField())
                .extractGroup(rule.getExtractGroup())
                .transform(rule.getTransform())
                .priority(rule.getPriority())
                .isActive(rule.getIsActive())
                .sourceScope(rule.getSourceScope())
                .createdBy(rule.getCreatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedBy(rule.getUpdatedBy())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
