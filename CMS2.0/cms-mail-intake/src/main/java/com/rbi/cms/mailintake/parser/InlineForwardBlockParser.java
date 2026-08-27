package com.rbi.cms.mailintake.parser;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared regex-matching logic for resolver #5 (a plain-text inline forward block) and resolver #4
 * (the same pattern applied to a TNEF-decoded body, where Outlook often embeds the same style of
 * "-----Original Message-----" block instead of a proper message/rfc822 part). Patterns are
 * externally configurable per language — see cms.mail.intake.resolver.inline-forward-patterns —
 * so adding a new Outlook locale is a config change, not a code change.
 */
@Component
class InlineForwardBlockParser {

    // Patterns are compiled once per distinct regex string and reused — languages don't change
    // at runtime, but re-compiling per message would be wasteful.
    private final Map<String, Pattern> compiledCache = new ConcurrentHashMap<>();

    Optional<InlineForwardMatch> tryMatch(String bodyText, Map<String, String> patternsByLanguage) {
        if (bodyText == null || bodyText.isBlank()) {
            return Optional.empty();
        }
        for (String regex : patternsByLanguage.values()) {
            Pattern pattern = compiledCache.computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.DOTALL));
            Matcher matcher = pattern.matcher(bodyText);
            if (matcher.find()) {
                InlineForwardMatch match = new InlineForwardMatch(
                        groupOrNull(matcher, "from"),
                        groupOrNull(matcher, "sent"),
                        groupOrNull(matcher, "to"),
                        groupOrNull(matcher, "subject"));
                if (match.from() != null) {
                    return Optional.of(match);
                }
            }
        }
        return Optional.empty();
    }

    private static String groupOrNull(Matcher matcher, String name) {
        try {
            String value = matcher.group(name);
            return value == null ? null : value.trim();
        } catch (IllegalArgumentException noSuchGroup) {
            return null; // a custom-configured pattern might not define every named group
        }
    }
}
