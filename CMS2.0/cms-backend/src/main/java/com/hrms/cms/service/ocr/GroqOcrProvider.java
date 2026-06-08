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
@ConditionalOnProperty(name = "cms.ocr.provider", havingValue = "groq")
public class GroqOcrProvider implements OcrProvider {

    @Value("${cms.ocr.groq-api-key:}")
    private String apiKey;

    @Value("${cms.ocr.groq-model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Override
    public String getProviderName() {
        return "groq";
    }

    @Override
    public Map<String, String> extractFields(byte[] fileBytes, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Groq API key not configured (cms.ocr.groq-api-key)");
            return Collections.emptyMap();
        }

        try {
            // Groq vision only supports images, not PDFs
            // For PDFs, fall back to text-only extraction prompt
            if ("application/pdf".equals(mimeType)) {
                return extractFromPdfAsText(fileBytes);
            }

            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64Data;

            Map<String, Object> requestBody = buildRequest(dataUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<String> response = restTemplate.exchange(
                    GROQ_URL, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }

            log.error("Groq returned: {}", response.getStatusCode());
            return Collections.emptyMap();

        } catch (Exception e) {
            log.error("Groq OCR failed: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private Map<String, String> extractFromPdfAsText(byte[] pdfBytes) {
        // Extract raw text from PDF using basic parsing
        String pdfText = extractTextFromPdf(pdfBytes);
        if (pdfText.isBlank()) {
            log.warn("Could not extract text from PDF");
            return Collections.emptyMap();
        }

        log.info("Extracted {} chars from PDF, sending to Groq for structuring", pdfText.length());

        // Send extracted text to Groq for field extraction
        Map<String, Object> textContent = Map.of("type", "text",
                "text", OcrPrompts.EXTRACTION_PROMPT + "\n\nHere is the text from the complaint letter:\n\n" + pdfText);

        Map<String, Object> userMsg = Map.of("role", "user", "content", List.of(textContent));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(userMsg),
                "temperature", 0.1,
                "max_tokens", 2048
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    GROQ_URL, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
        } catch (Exception e) {
            log.error("Groq text extraction failed: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    private String extractTextFromPdf(byte[] pdfBytes) {
        // Simple PDF text extraction — look for text between BT/ET markers or stream content
        try {
            String content = new String(pdfBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            StringBuilder text = new StringBuilder();

            // Try to find readable text in PDF (works for text-based PDFs)
            String[] lines = content.split("\n");
            for (String line : lines) {
                // PDF text objects contain parenthesized strings
                int idx = 0;
                while ((idx = line.indexOf('(', idx)) >= 0) {
                    int end = line.indexOf(')', idx);
                    if (end > idx) {
                        String fragment = line.substring(idx + 1, end);
                        if (fragment.length() > 1 && fragment.matches(".*[a-zA-Z].*")) {
                            text.append(fragment).append(" ");
                        }
                        idx = end + 1;
                    } else {
                        break;
                    }
                }
            }

            String result = text.toString().trim();
            // If we couldn't extract text, the PDF is likely image-based
            if (result.length() < 20) {
                log.info("PDF appears to be image-based (no extractable text). Consider uploading as image instead.");
                return "";
            }
            return result;
        } catch (Exception e) {
            return "";
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
