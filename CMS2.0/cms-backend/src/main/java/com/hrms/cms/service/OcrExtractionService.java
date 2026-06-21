package com.hrms.cms.service;

import com.hrms.cms.service.ocr.OcrProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates OCR extraction through a configurable, ordered provider chain.
 *
 * Configuration (application.yml):
 *
 *   cms.ocr.chain: groq, paddle        # try Groq first; fall back to PaddleOCR
 *   cms.ocr.chain: groq, gemini, paddle # three-level chain
 *
 * Each name must match the value returned by OcrProvider.getProviderName().
 * Providers absent from the chain, or whose isAvailable() returns false, are skipped.
 *
 * Adding your own OCR provider:
 *   1. Implement OcrProvider and annotate with @Component.
 *   2. Return a unique name from getProviderName() (e.g. "myocr").
 *   3. Return false from isAvailable() when the provider is not configured.
 *   4. Add "myocr" to cms.ocr.chain in application.yml — done.
 */
@Service
@Slf4j
public class OcrExtractionService {

    /** Ordered provider chain, e.g. "groq, paddle" or "groq, gemini, paddle" */
    @Value("${cms.ocr.chain:groq}")
    private String chainConfig;

    private final List<OcrProvider> orderedChain;

    public OcrExtractionService(List<OcrProvider> allProviders,
                                @Value("${cms.ocr.chain:groq}") String chainConfig) {
        Map<String, OcrProvider> byName = allProviders.stream()
                .collect(Collectors.toMap(OcrProvider::getProviderName, p -> p));

        this.orderedChain = Arrays.stream(chainConfig.split("[,\\s]+"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> {
                    OcrProvider p = byName.get(name);
                    if (p == null) {
                        log.warn("OCR chain: unknown provider '{}' — skipping", name);
                    } else if (!p.isAvailable()) {
                        log.warn("OCR chain: provider '{}' is not configured (missing API key/URL) — skipping", name);
                    }
                    return p;
                })
                .filter(p -> p != null && p.isAvailable())
                .collect(Collectors.toList());

        if (orderedChain.isEmpty()) {
            log.warn("OCR chain is empty — no providers available. Check cms.ocr.chain and provider credentials.");
        } else {
            log.info("OCR provider chain: {}",
                    orderedChain.stream().map(OcrProvider::getProviderName).collect(Collectors.joining(" → ")));
        }
    }

    /**
     * Run OCR through the provider chain.
     * Returns the result of the first provider that produces at least one field.
     * Returns an empty map if every provider in the chain fails or returns nothing.
     */
    public Map<String, String> extractFromImage(byte[] fileBytes, String mimeType) {
        if (orderedChain.isEmpty()) {
            log.warn("No OCR providers available. Set cms.ocr.chain and provide credentials.");
            return Collections.emptyMap();
        }

        for (OcrProvider provider : orderedChain) {
            log.info("OCR attempt: provider '{}'", provider.getProviderName());
            try {
                Map<String, String> result = provider.extractFields(fileBytes, mimeType);
                if (result != null && !result.isEmpty()) {
                    log.info("OCR success via '{}': {} fields extracted", provider.getProviderName(), result.size());
                    return result;
                }
                log.warn("OCR provider '{}' returned empty result — trying next in chain", provider.getProviderName());
            } catch (Exception e) {
                log.error("OCR provider '{}' threw exception: {} — trying next in chain",
                        provider.getProviderName(), e.getMessage());
            }
        }

        log.error("All OCR providers exhausted — returning empty result");
        return Collections.emptyMap();
    }

    /** Returns names of all active (available + in chain) providers, in order. */
    public List<String> getActiveChain() {
        return orderedChain.stream().map(OcrProvider::getProviderName).collect(Collectors.toList());
    }

    /** @deprecated Use getActiveChain() */
    @Deprecated
    public String getActiveProviderName() {
        return orderedChain.isEmpty() ? "none" : orderedChain.get(0).getProviderName();
    }
}
