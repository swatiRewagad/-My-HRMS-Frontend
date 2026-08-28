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
 * OCR provider backed by an external OCR service deployed on a VM.
 *
 * Endpoint: POST {cms.ocr.external-url}/v1/documents
 * Accepts multipart/form-data with 'file' field.
 * Returns JSON with extracted fields.
 *
 * Activated when cms.ocr.external-url is set (e.g. http://192.168.x.x:5000).
 * Add "external" to cms.ocr.chain to use it.
 */
@Component
@Slf4j
public class ExternalOcrProvider implements OcrProvider {

    @Value("${cms.ocr.external-url:}")
    private String externalUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExternalOcrProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() { return "external"; }

    @Override
    public boolean isAvailable() { return externalUrl != null && !externalUrl.isBlank(); }

    @Override
    public Map<String, String> extractFields(byte[] fileBytes, String mimeType) {
        try {
            String endpoint = externalUrl.replaceAll("/+$", "") + "/v1/documents";
            log.info("Sending {} bytes ({}) to external OCR at {}", fileBytes.length, mimeType, endpoint);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(mimeType));
            fileHeaders.setContentDispositionFormData("file", "document" + getExtension(mimeType));
            body.add("file", new HttpEntity<>(fileBytes, fileHeaders));

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }

            log.warn("External OCR returned HTTP {}", response.getStatusCode());
            return Collections.emptyMap();

        } catch (Exception e) {
            log.error("External OCR provider failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, String> parseResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        Map<String, String> result = new LinkedHashMap<>();

        // Try common response structures:
        // 1. { "fields": { "complainantName": "...", ... } }
        // 2. { "data": { "complainantName": "...", ... } }
        // 3. Flat: { "complainantName": "...", ... }
        JsonNode fields = root.path("fields");
        if (fields.isMissingNode()) fields = root.path("data");
        if (fields.isMissingNode()) fields = root;

        fields.fields().forEachRemaining(e -> {
            String key = e.getKey();
            if (key.equals("success") || key.equals("message") || key.equals("timestamp")
                    || key.equals("provider") || key.equals("raw_text")) return;
            String val = e.getValue().isNull() ? "" : e.getValue().asText().trim();
            if (!val.isEmpty()) result.put(key, val);
        });

        log.info("External OCR extracted {} fields", result.size());
        return result;
    }

    private String getExtension(String mimeType) {
        if (mimeType == null) return ".bin";
        return switch (mimeType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/tiff" -> ".tiff";
            default -> ".bin";
        };
    }
}
