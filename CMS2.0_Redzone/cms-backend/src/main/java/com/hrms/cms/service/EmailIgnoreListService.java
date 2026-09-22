package com.hrms.cms.service;

import com.hrms.cms.entity.EmailIgnoreEntry;
import com.hrms.cms.repository.EmailIgnoreEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Exceptional Email Master matching. Reads the rules from the database on every call so an admin
 * edit takes effect on the next ingestion without a restart.
 *
 * Returns the matched rule rather than a boolean: the suppression report has to name which rule
 * dropped the mail, and a boolean throws that away.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailIgnoreListService {

    private final EmailIgnoreEntryRepository ignoreRepository;

    public record MatchContext(String senderEmail, String subject, String toRecipients,
                               String ccRecipients, String bccRecipients) {}

    public List<EmailIgnoreEntry> activeRules() {
        return ignoreRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    /**
     * @return the first active rule that suppresses this email, or empty if none does.
     */
    public Optional<EmailIgnoreEntry> findMatchingRule(MatchContext ctx) {
        for (EmailIgnoreEntry rule : activeRules()) {
            if (!matchesRule(rule, ctx)) {
                continue;
            }
            if (isExcepted(rule, ctx)) {
                log.info("Ignore rule {} matched but its exception pattern also matched — draft will be created",
                        rule.getId());
                continue;
            }
            return Optional.of(rule);
        }
        return Optional.empty();
    }

    private boolean matchesRule(EmailIgnoreEntry rule, MatchContext ctx) {
        // Any header-specific pattern present must match. This makes To/CC/BCC/Subject rules real
        // rather than decorative, which is only possible because those values are now persisted.
        boolean anyCriterion = false;

        String primary = primaryPatternTarget(rule, ctx);
        if (isSet(rule.getEmailPattern())) {
            anyCriterion = true;
            if (!matches(primary, rule.getEmailPattern(), rule.getPatternType())) return false;
        }
        if (isSet(rule.getToPattern())) {
            anyCriterion = true;
            if (!matches(ctx.toRecipients(), rule.getToPattern(), rule.getPatternType())) return false;
        }
        if (isSet(rule.getCcPattern())) {
            anyCriterion = true;
            if (!matches(ctx.ccRecipients(), rule.getCcPattern(), rule.getPatternType())) return false;
        }
        if (isSet(rule.getBccPattern())) {
            anyCriterion = true;
            if (!matches(ctx.bccRecipients(), rule.getBccPattern(), rule.getPatternType())) return false;
        }
        if (isSet(rule.getSubjectPattern())) {
            anyCriterion = true;
            if (!matches(ctx.subject(), rule.getSubjectPattern(), "CONTAINS")) return false;
        }

        // A rule with no criteria must never match everything.
        return anyCriterion;
    }

    private String primaryPatternTarget(EmailIgnoreEntry rule, MatchContext ctx) {
        String field = rule.getMatchField() == null ? "FROM" : rule.getMatchField().toUpperCase();
        return switch (field) {
            case "TO" -> ctx.toRecipients();
            case "CC" -> ctx.ccRecipients();
            case "BCC" -> ctx.bccRecipients();
            case "SUBJECT" -> ctx.subject();
            default -> ctx.senderEmail();
        };
    }

    private boolean isExcepted(EmailIgnoreEntry rule, MatchContext ctx) {
        String exception = rule.getExceptionPattern();
        if (!isSet(exception)) return false;
        return matches(ctx.senderEmail(), exception, "CONTAINS")
                || matches(ctx.subject(), exception, "CONTAINS")
                || matches(ctx.toRecipients(), exception, "CONTAINS")
                || matches(ctx.ccRecipients(), exception, "CONTAINS")
                || matches(ctx.bccRecipients(), exception, "CONTAINS");
    }

    private boolean matches(String value, String pattern, String patternType) {
        if (value == null || value.isBlank() || pattern == null || pattern.isBlank()) return false;
        String v = value.toLowerCase().trim();
        String p = pattern.toLowerCase().trim();
        String type = patternType == null ? "EXACT" : patternType.toUpperCase();

        return switch (type) {
            case "DOMAIN" -> {
                String domain = p.startsWith("@") ? p.substring(1) : p;
                yield v.endsWith("@" + domain) || v.endsWith("." + domain) || v.contains("@" + domain);
            }
            case "CONTAINS" -> v.contains(p);
            case "WILDCARD" -> v.matches(wildcardToRegex(p));
            default -> v.equals(p) || v.contains(p + ",") || v.contains("," + p)
                    || List.of(v.split("[,;]")).stream().map(String::trim).anyMatch(s -> s.equals(p));
        };
    }

    private String wildcardToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        return regex.toString();
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
