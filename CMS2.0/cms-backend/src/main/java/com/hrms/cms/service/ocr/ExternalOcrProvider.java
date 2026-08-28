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
 * Form fields: file (required), sender (optional), subject (optional), synthetic (conditional).
 *
 * The 'synthetic' field is required under the 'laptop' deployment profile of the OCR service
 * (declares that payload is test data). On production 'cpu_node' profile it is ignored.
 * Set cms.ocr.external-synthetic=true for dev/QA environments talking to a laptop-profile instance.
 *
 * Activated when cms.ocr.external-url is set (e.g. http://192.168.x.x:8000).
 * Add "external" to cms.ocr.chain to use it.
 */
@Component
@Slf4j
public class ExternalOcrProvider implements OcrProvider {

    @Value("${cms.ocr.external-url:}")
    private String externalUrl;

    @Value("${cms.ocr.external-synthetic:false}")
    private boolean synthetic;

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
        return extractFields(fileBytes, mimeType, null, null);
    }

    public Map<String, String> extractFields(byte[] fileBytes, String mimeType, String sender, String subject) {
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

            if (sender != null && !sender.isBlank()) {
                body.add("sender", sender);
            }
            if (subject != null && !subject.isBlank()) {
                body.add("subject", subject);
            }
            if (synthetic) {
                body.add("synthetic", "true");
            }

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
