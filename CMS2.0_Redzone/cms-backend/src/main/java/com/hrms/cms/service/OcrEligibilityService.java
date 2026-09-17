package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Decides whether content may be OCR'd, BEFORE any OCR call is made.
 *
 * Policy: OCR typed English only. Vernacular content is never OCR'd and never machine-translated
 * into the complaint body — the citizen's own words stay the record and a human handles it.
 *
 * HONEST LIMITATION: this is not a handwriting classifier. No handwriting detection exists in this
 * system. What is enforced here is a fail-closed *scan-quality and script* gate: content that is
 * not confidently typed English is routed to a human rather than guessed at.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrEligibilityService {

    private final LanguageTranslationService translationService;

    @Value("${cms.ocr.min-confidence-for-prefill:70}")
    private int minConfidenceForPrefill;

    public enum Decision {
        /** Typed English: OCR and prefill. */
        OCR_ALLOWED,
        /** Non-Latin script detected: no OCR, route to a skilled DEO. */
        VERNACULAR_MANUAL_ENTRY,
        /** OCR ran but the result is not trustworthy enough to prefill a legal record. */
        LOW_CONFIDENCE_MANUAL_ENTRY
    }

    public record Assessment(Decision decision, String detectedLanguage, String languageName,
                             boolean vernacular, String reasonKey) {
        public boolean ocrAllowed() {
            return decision == Decision.OCR_ALLOWED;
        }
        public boolean requiresManualEntry() {
            return decision != Decision.OCR_ALLOWED;
        }
    }

    /**
     * Assess text (email body, or text already extracted from an attachment) for OCR eligibility.
     */
    public Assessment assessText(String text) {
        String detected = translationService.detectLanguage(text);
        boolean vernacular = !"en".equals(detected);
        if (vernacular) {
            return new Assessment(Decision.VERNACULAR_MANUAL_ENTRY, detected,
                    translationService.languageName(detected), true,
                    "intake.vernacular_manual_entry_required");
        }
        return new Assessment(Decision.OCR_ALLOWED, detected,
                translationService.languageName(detected), false, null);
    }

    /**
     * Second gate, applied to text the OCR provider returned. Catches the case the body-only check
     * cannot see: an English covering email with a vernacular scanned letter attached.
     */
    public Assessment assessExtractedText(String extractedText, Integer providerConfidence) {
        Assessment scriptAssessment = assessText(extractedText);
        if (scriptAssessment.requiresManualEntry()) {
            return scriptAssessment;
        }
        if (providerConfidence == null || providerConfidence < minConfidenceForPrefill) {
            return new Assessment(Decision.LOW_CONFIDENCE_MANUAL_ENTRY,
                    scriptAssessment.detectedLanguage(), scriptAssessment.languageName(), false,
                    "intake.ocr_low_confidence_manual_entry");
        }
        return scriptAssessment;
    }

    public int getMinConfidenceForPrefill() {
        return minConfidenceForPrefill;
    }
}
