package com.hrms.cms.service;

import com.hrms.cms.dto.ExtractionTestResponse;
import com.hrms.cms.entity.ExtractionRule;
import com.hrms.cms.repository.ExtractionRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class RuleBasedExtractor {

    private final ExtractionRuleRepository ruleRepository;

    private static final Set<String> ALL_TARGET_FIELDS = Set.of(
            "complainantName", "complainantPhone", "complainantEmail",
            "complainantAddress", "complainantState", "complainantDistrict",
            "complainantPincode", "subject", "description",
            "entityName", "entityType", "category", "branchName",
            "amountInvolved", "letterDate", "transactionDate", "cpgramsNumber"
    );

    public Set<String> getAllTargetFields() {
        return ALL_TARGET_FIELDS;
    }

    public Map<String, String> extract(String subject, String body) {
        List<ExtractionRule> rules = ruleRepository.findByIsActiveTrueOrderByPriorityAsc();
        Map<String, String> extracted = new LinkedHashMap<>();

        for (ExtractionRule rule : rules) {
            String targetField = rule.getTargetField();
            if (extracted.containsKey(targetField)) continue;

            String textToSearch = getSearchText(rule.getSourceScope(), subject, body);
            String value = applyRule(rule, textToSearch);

            if (value != null && !value.isBlank()) {
                value = applyTransform(value, rule.getTransform());
                extracted.put(targetField, value);
                log.debug("Rule '{}' matched: {} = '{}'", rule.getRuleCode(), targetField, value);
            }
        }

        log.info("Rule-based extraction produced {} fields from {} active rules", extracted.size(), rules.size());
        return extracted;
    }

    public ExtractionTestResponse testExtraction(String subject, String body) {
        List<ExtractionRule> rules = ruleRepository.findByIsActiveTrueOrderByPriorityAsc();
        Map<String, String> extracted = new LinkedHashMap<>();
        List<ExtractionTestResponse.MatchDetail> matchDetails = new ArrayList<>();

        for (ExtractionRule rule : rules) {
            String targetField = rule.getTargetField();
            String textToSearch = getSearchText(rule.getSourceScope(), subject, body);
            MatchResult result = applyRuleWithPosition(rule, textToSearch);

            if (result != null) {
                String value = applyTransform(result.value, rule.getTransform());

                matchDetails.add(ExtractionTestResponse.MatchDetail.builder()
                        .ruleName(rule.getRuleName())
                        .ruleCode(rule.getRuleCode())
                        .targetField(targetField)
                        .extractedValue(value)
                        .priority(rule.getPriority())
                        .matchStart(result.start)
                        .matchEnd(result.end)
                        .build());

                if (!extracted.containsKey(targetField)) {
                    extracted.put(targetField, value);
                }
            }
        }

        List<String> unmatched = new ArrayList<>(ALL_TARGET_FIELDS);
        unmatched.removeAll(extracted.keySet());
        Collections.sort(unmatched);

        return ExtractionTestResponse.builder()
                .extractedFields(extracted)
                .matchDetails(matchDetails)
                .unmatchedFields(unmatched)
                .build();
    }

    private String getSearchText(String scope, String subject, String body) {
        return switch (scope != null ? scope : "BOTH") {
            case "SUBJECT" -> subject != null ? subject : "";
            case "BODY" -> body != null ? body : "";
            default -> (subject != null ? subject : "") + "\n" + (body != null ? body : "");
        };
    }

    private String applyRule(ExtractionRule rule, String text) {
        if (text == null || text.isBlank()) return null;

        if ("REGEX".equals(rule.getPatternType())) {
            return applyRegex(rule.getPattern(), rule.getExtractGroup(), text);
        } else {
            return applyKeywordList(rule.getPattern(), text);
        }
    }

    private MatchResult applyRuleWithPosition(ExtractionRule rule, String text) {
        if (text == null || text.isBlank()) return null;

        if ("REGEX".equals(rule.getPatternType())) {
            return applyRegexWithPosition(rule.getPattern(), rule.getExtractGroup(), text);
        } else {
            return applyKeywordWithPosition(rule.getPattern(), text);
        }
    }

    private String applyRegex(String regex, Integer group, String text) {
        try {
            Pattern p = Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                int g = (group != null && group <= m.groupCount()) ? group : 0;
                return m.group(g).trim();
            }
        } catch (Exception e) {
            log.warn("Invalid regex pattern '{}': {}", regex, e.getMessage());
        }
        return null;
    }

    private MatchResult applyRegexWithPosition(String regex, Integer group, String text) {
        try {
            Pattern p = Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                int g = (group != null && group <= m.groupCount()) ? group : 0;
                return new MatchResult(m.group(g).trim(), m.start(g), m.end(g));
            }
        } catch (Exception e) {
            log.warn("Invalid regex pattern '{}': {}", regex, e.getMessage());
        }
        return null;
    }

    private String applyKeywordList(String keywords, String text) {
        String textLower = text.toLowerCase();
        for (String keyword : keywords.split("[,;\\n]+")) {
            String kw = keyword.trim();
            if (!kw.isEmpty() && textLower.contains(kw.toLowerCase())) {
                return kw;
            }
        }
        return null;
    }

    private MatchResult applyKeywordWithPosition(String keywords, String text) {
        String textLower = text.toLowerCase();
        for (String keyword : keywords.split("[,;\\n]+")) {
            String kw = keyword.trim();
            if (!kw.isEmpty()) {
                int idx = textLower.indexOf(kw.toLowerCase());
                if (idx >= 0) {
                    return new MatchResult(kw, idx, idx + kw.length());
                }
            }
        }
        return null;
    }

    private String applyTransform(String value, String transform) {
        if (transform == null || value == null) return value;
        return switch (transform.toUpperCase()) {
            case "UPPERCASE" -> value.toUpperCase();
            case "LOWERCASE" -> value.toLowerCase();
            case "TRIM" -> value.trim();
            default -> value;
        };
    }

    private record MatchResult(String value, int start, int end) {}
}
