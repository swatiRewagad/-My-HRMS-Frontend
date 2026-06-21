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
public class HuggingFaceOcrProvider implements OcrProvider {

    @Value("${cms.ocr.huggingface-api-key:}")
    private String apiKey;

    @Value("${cms.ocr.huggingface-model:microsoft/trocr-large-printed}")
    private String model;

    @Value("${cms.ocr.huggingface-inference-url:https://api-inference.huggingface.co/models/}")
    private String inferenceBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getProviderName() { return "huggingface"; }

    @Override
    public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public Map<String, String> extractFields(byte[] fileBytes, String mimeType) {

        try {
            // Step 1: Use HuggingFace Inference API for OCR (image-to-text)
            String rawText = callHuggingFaceOcr(fileBytes);
            if (rawText == null || rawText.isBlank()) {
                log.warn("HuggingFace OCR returned empty text");
                return Collections.emptyMap();
            }

            log.info("HuggingFace OCR raw text length: {}", rawText.length());

            // Step 2: Use a text generation model to structure the OCR output
            return structureExtractedText(rawText);

        } catch (Exception e) {
            log.error("HuggingFace OCR failed: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private String callHuggingFaceOcr(byte[] fileBytes) {
        String url = inferenceBaseUrl + model;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(fileBytes, headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.isArray() && !root.isEmpty()) {
                return root.get(0).path("generated_text").asText("");
            }
            return root.path("generated_text").asText(root.asText());
        } catch (Exception e) {
            return response.getBody();
        }
    }

    private Map<String, String> structureExtractedText(String rawText) {
        // Use HuggingFace text-generation endpoint (e.g. Mistral/Llama) to structure text
        String textGenModel = "mistralai/Mistral-7B-Instruct-v0.3";
        String url = inferenceBaseUrl + textGenModel;

        String prompt = "<s>[INST] " + OcrPrompts.EXTRACTION_PROMPT +
                "\n\nHere is the OCR-extracted text from the complaint letter:\n\n" +
                rawText + " [/INST]";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "inputs", prompt,
                "parameters", Map.of("max_new_tokens", 1024, "temperature", 0.1)
        );

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String text;
                if (root.isArray() && !root.isEmpty()) {
                    text = root.get(0).path("generated_text").asText("");
                } else {
                    text = root.path("generated_text").asText("");
                }
                // Extract only the part after [/INST]
                int instEnd = text.lastIndexOf("[/INST]");
                if (instEnd > 0) {
                    text = text.substring(instEnd + 7);
                }
                return OcrResponseParser.parseJsonFromLlmResponse(text, objectMapper);
            }
        } catch (Exception e) {
            log.error("HuggingFace text-gen structuring failed: {}", e.getMessage());
        }

        return Collections.emptyMap();
    }
}
