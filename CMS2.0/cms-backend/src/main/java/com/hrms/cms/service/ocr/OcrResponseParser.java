package com.hrms.cms.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class OcrResponseParser {

    private OcrResponseParser() {}

    // Known field keys to anchor regex salvage
    private static final Set<String> KNOWN_KEYS = Set.of(
            "complainantName", "complainantAddress", "complainantState", "complainantDistrict",
            "complainantPincode", "complainantPhone", "complainantEmail", "subject", "description",
            "entityName", "entityType", "category", "branchName", "amountInvolved",
            "letterDate", "transactionDate"
    );

    public static Map<String, String> parseJsonFromLlmResponse(String text, ObjectMapper objectMapper) {
        if (text == null || text.isBlank()) return Collections.emptyMap();

        try {
            text = text.trim();

            // Strip markdown code fences
            if (text.startsWith("```json")) {
                text = text.substring(7);
            } else if (text.startsWith("```")) {
                text = text.substring(3);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();

            // Find JSON object boundaries
            int jsonStart = text.indexOf('{');
            int jsonEnd = text.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                text = text.substring(jsonStart, jsonEnd + 1);
            }

            // Pre-clean common Groq JSON corruption patterns before strict parse:
            //   "category": "ATM,\n  "  →  "category": "ATM"   (value ends with comma+newline)
            //   trailing comma before closing brace/bracket
            String cleaned = text
                    .replaceAll("\"([^\"]*?),\\s*\\n\\s*\"", "\"$1\"")
                    .replaceAll(",\\s*}", "}")
                    .replaceAll(",\\s*]", "]");

            // Try strict JSON parse on the cleaned text
            Map<String, String> strictResult = tryStrictParse(cleaned, objectMapper);
            if (!strictResult.isEmpty()) {
                log.info("Parsed {} fields from cleaned JSON", strictResult.size());
                return strictResult;
            }

            // Try strict parse on original (cleaning may have broken it)
            strictResult = tryStrictParse(text, objectMapper);
            if (!strictResult.isEmpty()) {
                log.info("Parsed {} fields from original JSON", strictResult.size());
                return strictResult;
            }

            // Regex fallback: handles the "key": "", "value" pattern that vision models sometimes produce
            return regexSalvage(text);

        } catch (Exception e) {
            log.error("Failed to parse JSON from LLM response: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<String, String> tryStrictParse(String text, ObjectMapper objectMapper) {
        try {
            JsonNode extracted = objectMapper.readTree(text);
            Map<String, String> result = new LinkedHashMap<>();
            extracted.fields().forEachRemaining(entry -> {
                String key = entry.getKey().trim();
                if (key.isEmpty()) return;                          // skip blank keys Groq emits
                String val = entry.getValue().isNull() ? "" : entry.getValue().asText().trim();
                val = val.replaceAll("[,;\\s]+$", "").trim();      // strip trailing comma/whitespace
                if (!val.isEmpty()) result.put(key, val);
            });
            return result;
        } catch (Exception e) {
            log.warn("JSON parse failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Salvages key-value pairs from malformed LLM output where values appear as bare strings
     * after an empty placeholder: "key": "", "actual_value"
     * Also handles well-formed "key": "value" pairs.
     */
    private static Map<String, String> regexSalvage(String text) {
        Map<String, String> result = new LinkedHashMap<>();

        // Find all positions of known keys with any value (including empty)
        Pattern keyPattern = Pattern.compile("\"(" + String.join("|", KNOWN_KEYS) + ")\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = keyPattern.matcher(text);

        List<int[]> keyMatches = new ArrayList<>(); // [start, end, keyGroup, valGroup]
        while (m.find()) {
            String key = m.group(1);
            String val = m.group(2).replaceAll("[,;\\s]+$", "").trim();
            if (!val.isBlank()) {
                result.put(key, val);
            } else {
                // Empty value — look for the next quoted string after this match as the real value
                int searchFrom = m.end();
                Pattern nextStr = Pattern.compile("\"([^\"]{1,200})\"");
                Matcher ns = nextStr.matcher(text.substring(searchFrom, Math.min(searchFrom + 300, text.length())));
                if (ns.find()) {
                    String candidate = ns.group(1).trim();
                    // Skip if it looks like another JSON key (matches a known key name)
                    if (!candidate.isBlank() && !KNOWN_KEYS.contains(candidate)) {
                        result.put(key, candidate);
                    }
                }
            }
        }

        if (!result.isEmpty()) {
            log.info("Regex salvage extracted {} fields", result.size());
        } else {
            log.warn("Regex salvage found no fields");
        }
        return result;
    }
}
