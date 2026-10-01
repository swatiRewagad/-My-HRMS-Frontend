package com.hrms.cms.service.ocr;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads and strips the underscore-prefixed metadata OCR providers mix into their field map.
 *
 * <h2>Why this exists as a shared utility</h2>
 * {@code OcrExtractionService} returns a bare {@code Map<String, String>} in which
 * {@code PaddleOcrProvider} also places {@code _confidence}, {@code _typedDigital} and
 * {@code _ocrSource}. Two private copies of the reading logic already existed inside
 * {@code EmailSyndicationApiController}, and the interactive OCR endpoint had NEITHER — so it leaked
 * {@code _confidence} straight into the UI as though it were a complaint field, and never read the
 * confidence it was being handed.
 *
 * <p>The confidence value decides whether a scan may pre-populate a legal record, so a third divergent
 * copy was not acceptable. One implementation, used by every path.
 */
public final class OcrProviderMetadata {

    /** Provider metadata keys are prefixed with this; it is not a complaint field. */
    private static final String METADATA_PREFIX = "_";

    private OcrProviderMetadata() {
    }

    /**
     * The provider's confidence on a 0-100 scale, or null when it reported none.
     *
     * <p>Null is meaningful and must not be defaulted: only PaddleOCR reports a confidence, and the
     * callers decide what an absent signal means for their path. An earlier version of this logic
     * substituted a hardcoded 85, which made an unmeasured scan indistinguishable from a good one.
     */
    public static Integer confidenceOf(Map<String, String> extracted) {
        if (extracted == null) {
            return null;
        }
        String raw = extracted.get("_confidence");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            // Providers report either 0-1 or 0-100.
            return (int) Math.round(value <= 1.0 ? value * 100 : value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True when the provider reported the source as embedded/typed text rather than a rendered scan. */
    public static boolean isTypedDigital(Map<String, String> extracted) {
        return extracted != null && Boolean.parseBoolean(extracted.get("_typedDigital"));
    }

    public static String sourceOf(Map<String, String> extracted) {
        return extracted == null ? null : extracted.get("_ocrSource");
    }

    /** The extracted fields with provider metadata removed, preserving order. */
    public static Map<String, String> withoutMetadata(Map<String, String> extracted) {
        if (extracted == null) {
            return new LinkedHashMap<>();
        }
        return extracted.entrySet().stream()
                .filter(e -> !e.getKey().startsWith(METADATA_PREFIX))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** Concatenates the extracted values into one blob for script/language detection. */
    public static String textForLanguageDetection(Map<String, String> extracted) {
        return String.join("\n", withoutMetadata(extracted).values());
    }
}
