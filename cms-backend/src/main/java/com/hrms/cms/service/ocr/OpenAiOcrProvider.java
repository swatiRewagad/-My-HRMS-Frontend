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
@ConditionalOnProperty(name = "cms.ocr.provider", havingValue = "openai")
public class OpenAiOcrProvider implements OcrProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${cms.ocr.openai.api-key:}")
    private String apiKey;

    @Value("${cms.ocr.openai.model:gpt-4o}")
    private String model;

    public OpenAiOcrProvider(ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
            .baseUrl("https://api.openai.com")
            .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public OcrResult extractText(byte[] imageData, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API key not configured, returning empty result");
            return OcrResult.builder()
                .rawText("")
                .language("unknown")
                .confidence(0)
                .provider("openai")
                .build();
        }

        String base64Image = Base64.getEncoder().encodeToString(imageData);
        String dataUrl = "data:" + mimeType + ";base64," + base64Image;

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "messages", List.of(
                Map.of(
                    "role", "user",
                    "content", List.of(
                        Map.of(
                            "type", "image_url",
                            "image_url", Map.of("url", dataUrl)
                        ),
                        Map.of(
                            "type", "text",
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
                    )
                )
            ),
            "max_tokens", 4096,
            "temperature", 0.1
        );

        try {
            String response = webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseOpenAiResponse(response);
        } catch (Exception e) {
            log.error("OpenAI OCR failed: {}", e.getMessage());
            return OcrResult.builder()
                .rawText("")
                .language("unknown")
                .confidence(0)
                .provider("openai")
                .build();
        }
    }

    private OcrResult parseOpenAiResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            String cleanText = content.trim();
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
                .provider("openai")
                .build();
        } catch (Exception e) {
            log.warn("Failed to parse OpenAI response: {}", e.getMessage());
            return OcrResult.builder()
                .rawText(response != null ? response : "")
                .language("unknown")
                .confidence(50)
                .provider("openai")
                .build();
        }
    }
}
