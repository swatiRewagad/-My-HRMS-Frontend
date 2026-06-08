package com.hrms.cms.service;

import com.hrms.cms.service.ocr.OcrProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Facade service that delegates OCR extraction to the configured provider.
 *
 * Configuration in application.yml:
 *
 *   cms.ocr.provider: gemini | openai | huggingface | azure
 *
 * Each provider has its own config keys:
 *   - gemini:      cms.ocr.gemini-api-key, cms.ocr.gemini-model
 *   - openai:      cms.ocr.openai-api-key, cms.ocr.openai-model
 *   - huggingface: cms.ocr.huggingface-api-key, cms.ocr.huggingface-model
 *   - azure:       cms.ocr.azure-endpoint, cms.ocr.azure-api-key, cms.ocr.azure-model
 */
@Service
@Slf4j
public class OcrExtractionService {

    private final OcrProvider activeProvider;

    @Value("${cms.ocr.provider:gemini}")
    private String configuredProvider;

    public OcrExtractionService(List<OcrProvider> providers) {
        if (providers.isEmpty()) {
            log.warn("No OCR providers found. Extraction will return empty results.");
            this.activeProvider = null;
        } else {
            this.activeProvider = providers.get(0);
            log.info("OCR provider active: {}", this.activeProvider.getProviderName());
        }
    }

    public Map<String, String> extractFromImage(byte[] fileBytes, String mimeType) {
        if (activeProvider == null) {
            log.warn("No OCR provider configured. Set cms.ocr.provider and provide API keys.");
            return Collections.emptyMap();
        }

        log.info("Running OCR extraction using provider: {}", activeProvider.getProviderName());
        return activeProvider.extractFields(fileBytes, mimeType);
    }

    public String getActiveProviderName() {
        return activeProvider != null ? activeProvider.getProviderName() : "none";
    }
}
