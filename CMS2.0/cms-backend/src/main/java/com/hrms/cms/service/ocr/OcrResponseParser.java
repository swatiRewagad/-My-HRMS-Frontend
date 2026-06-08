package com.hrms.cms.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class OcrResponseParser {

    private OcrResponseParser() {}

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

            // Try to find JSON object boundaries if there's surrounding text
            int jsonStart = text.indexOf('{');
            int jsonEnd = text.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                text = text.substring(jsonStart, jsonEnd + 1);
            }

            JsonNode extracted = objectMapper.readTree(text);
            Map<String, String> result = new LinkedHashMap<>();
            extracted.fields().forEachRemaining(entry -> {
                String val = entry.getValue().isNull() ? "" : entry.getValue().asText();
                if (!val.isBlank()) {
                    result.put(entry.getKey(), val);
                }
            });

            log.info("Parsed {} non-empty fields from LLM response", result.size());
            return result;

        } catch (Exception e) {
            log.error("Failed to parse JSON from LLM response: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
