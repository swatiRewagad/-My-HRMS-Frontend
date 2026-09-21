package com.hrms.cms.controller;

import com.hrms.cms.service.CepcAuditService;
import com.hrms.cms.service.OcrEligibilityService;
import com.hrms.cms.service.OcrExtractionService;
import com.hrms.cms.service.RuleBasedExtractor;
import com.hrms.cms.service.ocr.OcrProviderMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Interactive OCR for operator uploads (UST624-626, UST778).
 *
 * <h2>The gap this closes</h2>
 * {@code OcrEligibilityService} — the fail-closed script and confidence gate — was wired into EMAIL
 * INGEST only. This endpoint, which is what the RBIO and CRPC screens actually call to pre-populate a
 * form, had no language gate whatsoever. A Hindi or Bengali scan therefore pre-filled the form with
 * vernacular text, and the {@code VERNACULAR_MANUAL_ENTRY} verdict was only reached later at submit
 * time — on a draft the operator had already filled in from ungated output.
 *
 * <h2>What the response now carries</h2>
 * Beyond the extracted {@code data}, the response states WHETHER prefill is permitted
 * ({@code prefillAllowed}), WHY not when it is refused ({@code reasonKey}, a translation key), and which
 * fields came from OCR ({@code ocrFields}) so the client can mark provenance per field (UST624). The
 * provider's confidence is surfaced rather than leaked as a pseudo-field.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
public class OcrController {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("application/pdf", "image/jpeg", "image/png", "image/tiff");

    private final OcrExtractionService ocrService;
    private final RuleBasedExtractor ruleBasedExtractor;
    private final OcrEligibilityService ocrEligibilityService;
    private final CepcAuditService auditService;

    @GetMapping("/provider")
    public ResponseEntity<Map<String, Object>> getActiveProvider() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "provider", ocrService.getActiveProviderName(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Not implemented: OCR from an already-stored draft attachment.
     *
     * <p>This returned {@code success: true} with an empty {@code data} map, so its only caller
     * (draft-assessment's "scan attachment" button) always reported "OCR extraction returned no data" —
     * a permanently dead feature that looked like a working one that found nothing. It now says plainly
     * that it is not implemented, so the difference between "no text in this document" and "this endpoint
     * does nothing" is visible to the caller.
     */
    @PostMapping("/extract-from-draft")
    public ResponseEntity<Map<String, Object>> extractFromDraft(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("implemented", false);
        response.put("message", "Scanning a stored attachment is not implemented. "
                + "Upload the file to /api/v1/ocr/extract instead.");
        response.put("messageKey", "ocr.error_extract_from_draft_unavailable");
        response.put("data", Map.of());
        response.put("provider", ocrService.getActiveProviderName());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(501).body(response);
    }

    @PostMapping("/extract")
    public ResponseEntity<Map<String, Object>> extractFromDocument(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No file uploaded",
                    "messageKey", "ocr.error_no_file",
                    "timestamp", LocalDateTime.now().toString()
            ));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Unsupported file type. Allowed: PDF, JPEG, PNG, TIFF",
                    "messageKey", "ocr.error_unsupported_type",
                    "timestamp", LocalDateTime.now().toString()
            ));
        }

        try {
            byte[] fileBytes = file.getBytes();
            Map<String, String> raw = ocrService.extractFromImage(fileBytes, contentType);

            // Fallback: if AI OCR returned nothing, try rule-based extraction on raw text.
            if (raw.isEmpty()) {
                log.info("AI OCR returned empty — falling back to rule-based extraction");
                String rawText = extractRawText(fileBytes, contentType);
                if (rawText != null && !rawText.isBlank()) {
                    raw = ruleBasedExtractor.extract("", rawText);
                    if (!raw.isEmpty()) {
                        log.info("Rule-based fallback extracted {} fields", raw.size());
                    }
                }
            }

            Integer confidence = OcrProviderMetadata.confidenceOf(raw);
            Map<String, String> extracted = OcrProviderMetadata.withoutMetadata(raw);

            if (extracted.isEmpty()) {
                // UST626: a failure must never block the operator, but it must leave a trace. The audit
                // row is written asynchronously so a slow or failing audit insert cannot hold up — or
                // fail — an upload the operator is waiting on.
                auditOcrOutcome("OCR_NO_DATA",
                        "OCR produced no fields for an operator upload (" + contentType + ")",
                        Map.of("contentType", contentType,
                                "provider", ocrService.getActiveProviderName(),
                                "sizeBytes", fileBytes.length));

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("success", false);
                response.put("message", "No data could be extracted from this document. "
                        + "Please enter the details manually.");
                response.put("messageKey", "ocr.error_no_data_extracted");
                response.put("prefillAllowed", false);
                response.put("data", Map.of());
                response.put("ocrFields", List.of());
                response.put("provider", ocrService.getActiveProviderName());
                response.put("timestamp", LocalDateTime.now().toString());
                // 200, not an error status: "this scan yielded nothing" is a normal outcome the operator
                // handles by typing, not a fault they should see as a failure dialog.
                return ResponseEntity.ok(response);
            }

            // UST778 — THE GATE, on the path that never had one.
            OcrEligibilityService.Assessment assessment = ocrEligibilityService.assessOperatorUpload(
                    OcrProviderMetadata.textForLanguageDetection(raw), confidence);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("provider", ocrService.getActiveProviderName());
            response.put("confidence", confidence);
            response.put("detectedLanguage", assessment.detectedLanguage());
            response.put("languageName", assessment.languageName());
            response.put("vernacular", assessment.vernacular());
            response.put("timestamp", LocalDateTime.now().toString());

            if (assessment.requiresManualEntry()) {
                // Fails CLOSED: the extracted values are deliberately NOT returned. Returning them with a
                // "please do not use these" flag would leave it to the client to comply, and a legal
                // record must not depend on that. The operator types the details instead.
                log.info("Operator OCR upload routed to manual entry: {} (language={}, confidence={})",
                        assessment.decision(), assessment.detectedLanguage(), confidence);

                auditOcrOutcome(assessment.decision().name(),
                        "Operator OCR upload routed to manual entry",
                        Map.of("contentType", contentType,
                                "detectedLanguage", String.valueOf(assessment.detectedLanguage()),
                                "confidence", String.valueOf(confidence),
                                "decision", assessment.decision().name()));

                response.put("success", false);
                response.put("prefillAllowed", false);
                response.put("skipReason", assessment.decision().name());
                response.put("messageKey", assessment.reasonKey());
                response.put("message", assessment.vernacular()
                        ? "This document is not in English, so it must be entered manually to preserve "
                                + "the complainant's own words."
                        : "The scan quality is too low to pre-fill a complaint record reliably. "
                                + "Please enter the details manually.");
                response.put("data", Map.of());
                response.put("ocrFields", List.of());
                return ResponseEntity.ok(response);
            }

            response.put("success", true);
            response.put("prefillAllowed", true);
            response.put("message", "Extraction successful — " + extracted.size() + " fields found");
            response.put("messageKey", "ocr.extraction_successful");
            response.put("data", extracted);
            // UST624: the per-field provenance list. The client marks exactly these fields as
            // OCR-sourced, so a prefilled value is visually distinguishable from a typed one.
            response.put("ocrFields", new ArrayList<>(extracted.keySet()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // UST626: non-blocking. Audited, then reported as a normal "type it manually" outcome rather
            // than a 500 the operator cannot act on.
            log.error("OCR extraction failed for an operator upload: {}", e.getMessage(), e);
            auditOcrOutcome("OCR_FAILED", "OCR extraction threw: " + e.getMessage(),
                    Map.of("contentType", String.valueOf(contentType),
                            "error", String.valueOf(e.getMessage())));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("prefillAllowed", false);
            response.put("skipReason", "OCR_FAILED");
            response.put("message", "Automatic extraction is unavailable. Please enter the details manually.");
            response.put("messageKey", "ocr.error_extraction_failed");
            response.put("data", Map.of());
            response.put("ocrFields", List.of());
            response.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Records an OCR outcome without ever affecting the operator's request.
     *
     * <p>Uses {@code logActionAsync} and is additionally wrapped: the async variant is
     * {@code @Transactional}, and if the executor rejects the task or the bean is unavailable the
     * exception would otherwise surface on the request thread. UST626 requires that a failed OCR attempt
     * never presents a blocking error, and an audit write is not worth breaking an upload for.
     *
     * <p>{@code "N/A"} as the complaint number follows the existing convention for events with no
     * complaint in scope — at this point in the flow no complaint or draft exists yet.
     */
    private void auditOcrOutcome(String action, String remarks, Map<String, Object> metadata) {
        try {
            auditService.logActionAsync("N/A", "OCR_" + action, "SYSTEM", "OCR",
                    remarks, metadata, null, null);
        } catch (Exception e) {
            log.warn("Could not record the OCR audit entry (continuing regardless): {}", e.getMessage());
        }
    }

    private String extractRawText(byte[] fileBytes, String mimeType) {
        try {
            if ("application/pdf".equals(mimeType)) {
                try (PDDocument doc = Loader.loadPDF(fileBytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(doc);
                }
            }
            // For images, raw text extraction is not possible without an OCR engine.
            return "";
        } catch (Exception e) {
            log.warn("Failed to extract raw text from file: {}", e.getMessage());
            return "";
        }
    }
}
