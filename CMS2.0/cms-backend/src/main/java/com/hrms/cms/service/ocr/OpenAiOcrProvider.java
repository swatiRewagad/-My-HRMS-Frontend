package com.hrms.cms.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@Slf4j
public class OpenAiOcrProvider implements OcrProvider {

    @Value("${cms.ocr.openai-api-key:}")
    private String apiKey;

    @Value("${cms.ocr.openai-model:gpt-4o-mini}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    @Override
    public String getProviderName() { return "openai"; }

    @Override
    public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public Map<String, String> extractFields(byte[] fileBytes, String mimeType) {

        try {
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64Data;

            Map<String, Object> requestBody = buildRequest(dataUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<String> response = restTemplate.exchange(
                    OPENAI_URL, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }

            log.error("OpenAI returned: {}", response.getStatusCode());
            return Collections.emptyMap();

        } catch (Exception e) {
            log.error("OpenAI OCR failed: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> buildRequest(String dataUrl) {
        Map<String, Object> textContent = Map.of("type", "text", "text", OcrPrompts.EXTRACTION_PROMPT);
        Map<String, Object> imageUrl = Map.of("url", dataUrl);
        Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrl);

        Map<String, Object> userMsg = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );

        return Map.of(
                "model", model,
                "messages", List.of(userMsg),
                "temperature", 0.1,
                "max_tokens", 2048
        );
    }

    private Map<String, String> parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("choices").get(0).path("message").path("content").asText("");
        return OcrResponseParser.parseJsonFromLlmResponse(text, objectMapper);
    }
}
