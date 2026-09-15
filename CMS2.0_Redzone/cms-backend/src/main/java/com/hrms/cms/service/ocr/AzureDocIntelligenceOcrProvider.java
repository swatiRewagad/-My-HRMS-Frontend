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
public class AzureDocIntelligenceOcrProvider implements OcrProvider {

    @Value("${cms.ocr.azure-endpoint:}")
    private String endpoint;

    @Value("${cms.ocr.azure-api-key:}")
    private String apiKey;

    @Value("${cms.ocr.azure-model:prebuilt-read}")
    private String modelId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getProviderName() { return "azure"; }

    @Override
    public boolean isAvailable() { return apiKey != null && !apiKey.isBlank() && endpoint != null && !endpoint.isBlank(); }

    @Override
    public Map<String, String> extractFields(byte[] fileBytes, String mimeType) {

        try {
            // Step 1: Submit document for analysis
            String analyzeUrl = endpoint + "/formrecognizer/documentModels/" + modelId + ":analyze?api-version=2023-07-31";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Ocp-Apim-Subscription-Key", apiKey);
            headers.setContentType(MediaType.valueOf(mimeType));

            ResponseEntity<String> submitResponse = restTemplate.exchange(
                    analyzeUrl, HttpMethod.POST, new HttpEntity<>(fileBytes, headers), String.class);

            if (!submitResponse.getStatusCode().is2xxSuccessful()) {
                log.error("Azure submit failed: {}", submitResponse.getStatusCode());
                return Collections.emptyMap();
            }

            // Get result URL from Operation-Location header
            String resultUrl = submitResponse.getHeaders().getFirst("Operation-Location");
            if (resultUrl == null) return Collections.emptyMap();

            // Step 2: Poll for result
            Thread.sleep(3000);
            headers = new HttpHeaders();
            headers.set("Ocp-Apim-Subscription-Key", apiKey);

            ResponseEntity<String> resultResponse = restTemplate.exchange(
                    resultUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (resultResponse.getStatusCode().is2xxSuccessful() && resultResponse.getBody() != null) {
                return parseAzureResult(resultResponse.getBody());
            }

            return Collections.emptyMap();

        } catch (Exception e) {
            log.error("Azure OCR failed: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private Map<String, String> parseAzureResult(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode analyzeResult = root.path("analyzeResult");

        StringBuilder fullText = new StringBuilder();
        JsonNode pages = analyzeResult.path("pages");
        for (JsonNode page : pages) {
            for (JsonNode line : page.path("lines")) {
                fullText.append(line.path("content").asText("")).append("\n");
            }
        }

        // For now return raw extracted text in description, or parse key-value pairs
        Map<String, String> result = new LinkedHashMap<>();
        result.put("description", fullText.toString().trim());

        // If Azure returns key-value pairs (prebuilt-document model)
        JsonNode kvPairs = analyzeResult.path("keyValuePairs");
        for (JsonNode kv : kvPairs) {
            String key = kv.path("key").path("content").asText("").toLowerCase();
            String value = kv.path("value").path("content").asText("");
            mapAzureField(result, key, value);
        }

        return result;
    }

    private void mapAzureField(Map<String, String> result, String key, String value) {
        if (key.contains("name")) result.putIfAbsent("complainantName", value);
        else if (key.contains("address")) result.putIfAbsent("complainantAddress", value);
        else if (key.contains("phone") || key.contains("mobile")) result.putIfAbsent("complainantPhone", value);
        else if (key.contains("email")) result.putIfAbsent("complainantEmail", value);
        else if (key.contains("pin")) result.putIfAbsent("complainantPincode", value);
        else if (key.contains("subject")) result.putIfAbsent("subject", value);
        else if (key.contains("bank") || key.contains("entity")) result.putIfAbsent("entityName", value);
        else if (key.contains("amount")) result.putIfAbsent("amountInvolved", value);
        else if (key.contains("date")) result.putIfAbsent("letterDate", value);
    }
}
