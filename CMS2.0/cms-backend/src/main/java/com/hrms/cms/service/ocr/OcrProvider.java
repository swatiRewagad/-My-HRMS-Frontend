package com.hrms.cms.service.ocr;

import java.util.Map;

/**
 * Strategy interface for OCR extraction providers.
 *
 * To add a new provider:
 *   1. Implement this interface and annotate with @Component.
 *   2. Return a unique name from getProviderName() (e.g. "myocr").
 *   3. Return false from isAvailable() when the provider is not configured.
 *   4. Add your provider's name to cms.ocr.chain in application.yml.
 *
 * The chain is tried in order; the first provider that returns a non-empty
 * result wins. Unavailable providers are skipped automatically.
 */
public interface OcrProvider {

    /** Unique lowercase identifier, e.g. "groq", "paddle", "gemini". */
    String getProviderName();

    /**
     * Returns true when this provider has all required config (API key / URL).
     * Unavailable providers are never called.
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Extract complaint fields from raw file bytes.
     * Return an empty map (never null) when extraction fails or produces nothing.
     */
    Map<String, String> extractFields(byte[] fileBytes, String mimeType);
}
