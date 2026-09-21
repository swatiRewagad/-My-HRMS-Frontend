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

    /**
     * Whether an ABSENT confidence signal blocks prefill on the interactive operator path.
     *
     * <p>False by default. See {@link #assessOperatorUpload} for the reasoning: only PaddleOCR reports a
     * confidence, so making an absent signal blocking would disable operator prefill entirely in any
     * deployment whose chain is Groq-only — which is the dev-local default. Turning PaddleOCR on is what
     * should tighten this behaviour.
     */
    @Value("${cms.ocr.require-confidence-for-operator-prefill:false}")
    private boolean requireConfidenceForOperatorPrefill;

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

    /**
     * The gate for an INTERACTIVE operator upload — {@code POST /api/v1/ocr/extract} (UST778).
     *
     * <h2>Why this path needed its own method</h2>
     * Until now this service was wired into email ingest ONLY. The direct upload used by the RBIO and
     * CRPC screens had NO language gate at all, so a Hindi scan pre-filled the form with vernacular text
     * and was only stamped {@code VERNACULAR_MANUAL_ENTRY} later, at submit time, on a draft the operator
     * had already filled in.
     *
     * <h2>Why it is not simply {@link #assessExtractedText}</h2>
     * That method treats a null confidence as failing the gate, which is right for ingest: no human is
     * watching, so an unmeasurable scan must not silently populate a legal record. But only
     * {@code PaddleOcrProvider} emits a confidence signal, and the dev-local chain is {@code groq} alone
     * with {@code paddle-ocr-url} empty. Reusing it here would classify EVERY interactive upload as
     * {@code LOW_CONFIDENCE_MANUAL_ENTRY} and suppress all prefill — a gate that appears to work while
     * actually just disabling the feature.
     *
     * <h2>The distinction being drawn, stated explicitly</h2>
     * <ul>
     *   <li>The SCRIPT gate is absolute on both paths. Non-Latin content is never prefilled. That is
     *       UST778's actual requirement and it does not depend on any provider capability.</li>
     *   <li>The CONFIDENCE gate is absolute on ingest, and advisory here WHEN NO SIGNAL EXISTS. An
     *       operator uploading a document is looking at that document and at the form, and every
     *       prefilled field is marked with its provenance, so they can see what came from OCR. A
     *       measured-but-low confidence still blocks; an unmeasurable one warns.</li>
     * </ul>
     * Set {@code cms.ocr.require-confidence-for-operator-prefill=true} to make an absent signal blocking
     * here too. It is false by default so that enabling PaddleOCR is what tightens this, rather than a
     * config change nobody remembers to make.
     *
     * @param providerConfidence the provider's confidence, or null when the provider does not report one
     */
    public Assessment assessOperatorUpload(String extractedText, Integer providerConfidence) {
        Assessment scriptAssessment = assessText(extractedText);
        if (scriptAssessment.requiresManualEntry()) {
            return scriptAssessment;
        }

        if (providerConfidence == null) {
            if (requireConfidenceForOperatorPrefill) {
                return new Assessment(Decision.LOW_CONFIDENCE_MANUAL_ENTRY,
                        scriptAssessment.detectedLanguage(), scriptAssessment.languageName(), false,
                        "intake.ocr_low_confidence_manual_entry");
            }
            log.debug("OCR provider reported no confidence; allowing operator prefill with provenance "
                    + "marking. Enable PaddleOCR for a measured signal.");
            return scriptAssessment;
        }

        if (providerConfidence < minConfidenceForPrefill) {
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
