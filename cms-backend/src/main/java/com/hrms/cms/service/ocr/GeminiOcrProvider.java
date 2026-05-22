package com.hrms.cms.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "cms.ocr.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiOcrProvider implements OcrProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${cms.ocr.gemini.api-key:}")
    private String apiKey;

    @Value("${cms.ocr.gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${cms.ocr.gemini.timeout:60}")
    private int timeoutSeconds;

    public GeminiOcrProvider(ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public OcrResult extractText(byte[] imageData, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured, returning empty result");
            return OcrResult.builder()
                .rawText("")
                .language("unknown")
                .confidence(0)
                .provider("gemini")
                .build();
        }

        String base64Image = Base64.getEncoder().encodeToString(imageData);

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of(
                        "inlineData", Map.of(
                            "mimeType", mimeType,
                            "data", base64Image
                        )
                    ),
                    Map.of(
                        "text", """
                            Extract all text from this scanned document/image. This may be a handwritten or printed complaint letter in Hindi or English.

                            Return the response in this exact JSON format:
                            {
                              "rawText": "<complete extracted text as-is>",
                              "language": "<Hindi or English>",
                              "confidence": <number 0-100>
                            }

                            Rules:
                            - Extract ALL text exactly as written
                            - Detect whether it's Hindi or English
                            - Estimate OCR confidence (0-100)
                            - Return ONLY valid JSON, no markdown
                            """
                    )
                ))
            ),
            "generationConfig", Map.of(
                "temperature", 0.1,
                "maxOutputTokens", 8192
            )
        );

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = webClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .block();

                log.info("Gemini response received (attempt {}), length: {}", attempt, response != null ? response.length() : 0);
                return parseGeminiResponse(response);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if ((msg.contains("429") || msg.contains("503")) && attempt < maxRetries) {
                    log.warn("Gemini error (attempt {}): {}, retrying in {}s...", attempt, msg, attempt * 5);
                    try { Thread.sleep(attempt * 5000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    log.error("Gemini OCR failed (attempt {}): {}", attempt, msg);
                    break;
                }
            }
        }
        return OcrResult.builder()
            .rawText("")
            .language("unknown")
            .confidence(0)
            .provider("gemini")
            .build();
    }

    private OcrResult parseGeminiResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty()) {
                return OcrResult.builder().rawText("").language("unknown").confidence(0).provider("gemini").build();
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            String text = "";
            for (int i = parts.size() - 1; i >= 0; i--) {
                String partText = parts.get(i).path("text").asText("");
                if (!partText.isBlank()) {
                    text = partText;
                    break;
                }
            }

            // Try to parse as JSON response
            String cleanText = text.trim();
            if (cleanText.startsWith("```json")) {
                cleanText = cleanText.substring(7);
            }
            if (cleanText.startsWith("```")) {
                cleanText = cleanText.substring(3);
            }
            if (cleanText.endsWith("```")) {
                cleanText = cleanText.substring(0, cleanText.length() - 3);
            }
            cleanText = cleanText.trim();

            JsonNode parsed = objectMapper.readTree(cleanText);
            return OcrResult.builder()
                .rawText(parsed.path("rawText").asText(""))
                .language(parsed.path("language").asText("English"))
                .confidence(parsed.path("confidence").asInt(85))
                .provider("gemini")
                .build();
        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON response, using raw text: {}", e.getMessage());
            return OcrResult.builder()
                .rawText(response != null ? response : "")
                .language("unknown")
                .confidence(50)
                .provider("gemini")
                .build();
        }
    }
}
