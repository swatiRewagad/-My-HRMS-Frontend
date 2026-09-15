package com.hrms.cms.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OCR provider backed by a self-hosted PaddleOCR sidecar (cms-paddle-ocr).
 *
 * Activated when cms.ocr.paddle-ocr-url is set, regardless of the chain order.
 * The sidecar accepts raw file bytes, runs PaddleOCR for text extraction,
 * then applies rule-based field structuring — no external API key needed.
 *
 * Sidecar endpoint:  POST {paddle-ocr-url}/ocr
 *   multipart/form-data: file=<bytes>, mime_type=<string>
 *   Response: { "fields": {...}, "raw_text": "...", "success": true }
 */
@Component
@Slf4j
public class PaddleOcrProvider implements OcrProvider {

    @Value("${cms.ocr.paddle-ocr-url:}")
    private String paddleUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaddleOcrProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);  // PaddleOCR is CPU-bound; allow more time
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() { return "paddle"; }

    @Override
    public boolean isAvailable() { return paddleUrl != null && !paddleUrl.isBlank(); }

    @Override
    public Map<String, String> extractFields(byte[] fileBytes, String mimeType) {
        try {
            String endpoint = paddleUrl.stripTrailing() + "/ocr";
            log.info("Sending {} bytes ({}) to PaddleOCR sidecar at {}", fileBytes.length, mimeType, endpoint);

            // Multipart POST: file bytes + mime type
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("mime_type", mimeType);

            // Wrap bytes as a named resource
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(mimeType));
            body.add("file", new HttpEntity<>(fileBytes, fileHeaders));

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }

            log.warn("PaddleOCR sidecar returned HTTP {}", response.getStatusCode());
            return Collections.emptyMap();

        } catch (Exception e) {
            log.error("PaddleOCR provider failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, String> parseResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        if (!root.path("success").asBoolean(true)) {
            log.warn("PaddleOCR sidecar reported failure: {}", root.path("error").asText("unknown"));
            return Collections.emptyMap();
        }

        JsonNode fields = root.path("fields");
        if (fields.isMissingNode() || fields.isNull()) {
            log.warn("PaddleOCR response has no 'fields' object");
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();
        fields.fields().forEachRemaining(e -> {
            String val = e.getValue().isNull() ? "" : e.getValue().asText().trim();
            if (!val.isEmpty()) result.put(e.getKey(), val);
        });

        log.info("PaddleOCR extracted {} fields (raw_text length: {} chars)",
                result.size(), root.path("raw_text").asText("").length());
        return result;
    }
}
