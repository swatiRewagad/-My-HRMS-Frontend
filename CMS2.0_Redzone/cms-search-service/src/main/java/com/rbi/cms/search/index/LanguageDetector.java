package com.rbi.cms.search.index;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Index-time language tagging by Unicode script range.
 *
 * <p>No language-detection library is pulled in. Two reasons, in order of weight:
 *
 * <p>1. The thresholds and ranges here are a deliberate port of the heuristic already in production
 * as {@code cms-backend}'s {@code LanguageTranslationService.detectLanguage}. Two different answers
 * to "what language is this complaint" in one system is a defect, so this agrees with that one by
 * construction rather than by coincidence.
 *
 * <p>2. The only consumer is the choice of analyzer subfield to boost, and the mapping has exactly
 * four analyzed subfields (en/hi/bn/ur). A statistical detector's extra precision — telling Marathi
 * from Hindi, Assamese from Bengali — buys nothing, because both members of each of those pairs
 * resolve to the same subfield anyway. Devanagari cannot be split into hi/mr by script at all.
 *
 * <p>The resolution is intentionally lossy in the same places the script is: Devanagari reports
 * {@code hi} for Marathi text, and Bengali script reports {@code bn} for Assamese text.
 */
@Component
public class LanguageDetector {

    /** Share of letters that must belong to a script before the text counts as that language. */
    private static final double SCRIPT_THRESHOLD = 0.15;

    private static final Map<String, int[]> SCRIPT_RANGES = new LinkedHashMap<>();

    static {
        SCRIPT_RANGES.put("hi", new int[]{0x0900, 0x097F});
        SCRIPT_RANGES.put("bn", new int[]{0x0980, 0x09FF});
        SCRIPT_RANGES.put("ta", new int[]{0x0B80, 0x0BFF});
        SCRIPT_RANGES.put("te", new int[]{0x0C00, 0x0C7F});
        SCRIPT_RANGES.put("kn", new int[]{0x0C80, 0x0CFF});
        SCRIPT_RANGES.put("ml", new int[]{0x0D00, 0x0D7F});
        SCRIPT_RANGES.put("gu", new int[]{0x0A80, 0x0AFF});
        SCRIPT_RANGES.put("pa", new int[]{0x0A00, 0x0A7F});
        SCRIPT_RANGES.put("or", new int[]{0x0B00, 0x0B7F});
        SCRIPT_RANGES.put("ur", new int[]{0x0600, 0x06FF});
    }

    /**
     * Which analyzed subfield suffix a detected language maps onto.
     *
     * Only en/hi/bn/ur exist as analyzed subfields because those are the only four languages
     * Elasticsearch 8.15.3 can actually analyze beyond tokenisation — see the mapping JSON. Every
     * other language returns null and is searched on the base cms_indic field only.
     */
    public String analyzedSubfield(String language) {
        if (language == null) {
            return null;
        }
        return switch (language) {
            case "en" -> "en";
            case "hi", "mr" -> "hi";
            case "bn", "as" -> "bn";
            case "ur" -> "ur";
            default -> null;
        };
    }

    public String detect(String... texts) {
        StringBuilder combined = new StringBuilder();
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                combined.append(text).append(' ');
            }
        }
        return detectOne(combined.toString());
    }

    private String detectOne(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }

        // Ratio over letters only, not raw length: dividing by raw length let whitespace and Latin
        // punctuation dilute a genuinely vernacular message below the threshold, which is exactly
        // the bug the backend heuristic already carries a comment about.
        long letterCount = text.codePoints().filter(Character::isLetter).count();
        if (letterCount == 0) {
            return "en";
        }

        for (Map.Entry<String, int[]> entry : SCRIPT_RANGES.entrySet()) {
            int low = entry.getValue()[0];
            int high = entry.getValue()[1];
            long matches = text.codePoints().filter(c -> c >= low && c <= high).count();
            if (matches > letterCount * SCRIPT_THRESHOLD) {
                return entry.getKey();
            }
        }

        return "en";
    }
}
