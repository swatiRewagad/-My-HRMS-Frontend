package com.hrms.cms.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@Slf4j
@ConditionalOnProperty(name = "cms.ocr.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiOcrProvider implements OcrProvider {

    @Value("${cms.ocr.gemini-api-key:}")
    private String apiKey;

    @Value("${cms.ocr.gemini-model:gemini-2.0-flash}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public Map<String, String> extractFields(byte[] fileBytes, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured (cms.ocr.gemini-api-key)");
            return Collections.emptyMap();
        }

        try {
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            String url = String.format(GEMINI_URL, model, apiKey);

            Map<String, Object> requestBody = buildRequest(base64Data, mimeType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }

            log.error("Gemini returned: {}", response.getStatusCode());
            return Collections.emptyMap();

        } catch (Exception e) {
            log.error("Gemini OCR failed: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> buildRequest(String base64Data, String mimeType) {
        Map<String, Object> inlineData = new LinkedHashMap<>();
        inlineData.put("mimeType", mimeType);
        inlineData.put("data", base64Data);

        Map<String, Object> imagePart = Map.of("inlineData", inlineData);
        Map<String, Object> textPart = Map.of("text", OcrPrompts.EXTRACTION_PROMPT);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", List.of(textPart, imagePart));

        Map<String, Object> generationConfig = Map.of(
                "temperature", 0.1,
                "maxOutputTokens", 2048
        );

        return Map.of("contents", List.of(content), "generationConfig", generationConfig);
    }

    private Map<String, String> parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) return Collections.emptyMap();

        String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText("");
        return OcrResponseParser.parseJsonFromLlmResponse(text, objectMapper);
    }
}
