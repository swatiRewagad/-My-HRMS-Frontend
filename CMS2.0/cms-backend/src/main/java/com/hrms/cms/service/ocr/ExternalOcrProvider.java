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

        String status = root.path("status").asText("");
        if ("failed".equals(status) || "bounced".equals(status)) {
            log.warn("External OCR returned status='{}', job_id={}", status, root.path("job_id").asText());
            return Collections.emptyMap();
        }

        String jobId = root.path("job_id").asText("");
        boolean isNew = root.path("is_new").asBoolean(true);
        String softDuplicate = root.path("soft_duplicate_of").asText(null);

        if (!isNew) {
            log.info("External OCR: duplicate document (job_id={}), reusing previous result", jobId);
        }
        if (softDuplicate != null) {
            log.info("External OCR: soft duplicate of job_id={}, reason={}",
                    softDuplicate, root.path("soft_duplicate_reason").asText("unknown"));
        }

        JsonNode envelope = root.path("result");
        if (envelope.isMissingNode() || envelope.isNull()) {
            log.warn("External OCR response has no 'result' envelope (status={})", status);
            return Collections.emptyMap();
        }

        // The envelope structure: result.document.fields.{field_name}.value/normalized
        JsonNode document = envelope.path("document");
        if (document.isMissingNode() || document.isNull()) {
            log.warn("External OCR: envelope has no 'document' object");
            return Collections.emptyMap();
        }

        JsonNode fields = document.path("fields");
        if (!fields.isMissingNode() && !fields.isNull()) {
            fields.fields().forEachRemaining(e -> {
                String fieldName = e.getKey();
                JsonNode fieldObj = e.getValue();
                // Prefer normalized value (e.g. "45000.00"), fall back to value_en, then value
                String val = getNonBlank(fieldObj, "normalized");
                if (val == null) val = getNonBlank(fieldObj, "value_en");
                if (val == null) val = getNonBlank(fieldObj, "value");
                if (val != null) {
                    // Map OCR field names (snake_case) to frontend expected names (camelCase)
                    String mappedName = mapFieldName(fieldName);
                    result.put(mappedName, val);
                }
            });
        }

        // Extract complaint summary as description
        JsonNode summary = document.path("complaint_summary").path("text");
        if (!summary.isMissingNode() && !summary.isNull()) {
            String text = summary.asText("").trim();
            if (!text.isEmpty()) {
                result.put("description", text);
            }
        }

        log.info("External OCR extracted {} fields from envelope (job_id={}, status={})",
                result.size(), jobId, status);
        return result;
    }

    private String getNonBlank(JsonNode obj, String field) {
        JsonNode node = obj.path(field);
        if (node.isMissingNode() || node.isNull()) return null;
        String val = node.asText("").trim();
        return val.isEmpty() ? null : val;
    }

    private static final Map<String, String> FIELD_NAME_MAP = Map.ofEntries(
            Map.entry("complainant_name", "complainantName"),
            Map.entry("complainant_address", "complainantAddress"),
            Map.entry("complainant_state", "complainantState"),
            Map.entry("complainant_district", "complainantDistrict"),
            Map.entry("complainant_pincode", "complainantPincode"),
            Map.entry("complainant_phone", "complainantPhone"),
            Map.entry("complainant_email", "complainantEmail"),
            Map.entry("bank_name", "entityName"),
            Map.entry("entity_name", "entityName"),
            Map.entry("entity_type", "entityType"),
            Map.entry("branch_name", "branchName"),
            Map.entry("ifsc_code", "ifscCode"),
            Map.entry("account_number", "accountNumber"),
            Map.entry("amount", "amountInvolved"),
            Map.entry("transaction_date", "transactionDate"),
            Map.entry("letter_date", "letterDate"),
            Map.entry("subject", "subject"),
            Map.entry("category", "category"),
            Map.entry("prior_complaint_reference", "complaintRef")
    );

    private String mapFieldName(String ocrFieldName) {
        return FIELD_NAME_MAP.getOrDefault(ocrFieldName, ocrFieldName);
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
