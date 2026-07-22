package com.hrms.cms.controller;

import com.hrms.cms.service.OcrExtractionService;
import com.hrms.cms.service.RuleBasedExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrExtractionService ocrService;
    private final RuleBasedExtractor ruleBasedExtractor;

    @GetMapping("/provider")
    public ResponseEntity<Map<String, Object>> getActiveProvider() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "provider", ocrService.getActiveProviderName(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/extract-from-draft")
    public ResponseEntity<Map<String, Object>> extractFromDraft(@RequestBody Map<String, Object> request) {
        // In production, this would retrieve the stored attachment file and run OCR
        // For now, it returns a placeholder indicating that OCR should be done via file upload
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Use /api/v1/ocr/extract with file upload for OCR scanning");
        response.put("data", Map.of());
        response.put("provider", ocrService.getActiveProviderName());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/extract")
    public ResponseEntity<Map<String, Object>> extractFromDocument(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No file uploaded",
                    "timestamp", LocalDateTime.now().toString()
            ));
        }

        String contentType = file.getContentType();
        Set<String> allowed = Set.of("application/pdf", "image/jpeg", "image/png", "image/tiff");
        if (contentType == null || !allowed.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Unsupported file type. Allowed: PDF, JPEG, PNG, TIFF",
                    "timestamp", LocalDateTime.now().toString()
            ));
        }

        try {
            byte[] fileBytes = file.getBytes();
            Map<String, String> extracted = ocrService.extractFromImage(fileBytes, contentType);

            // Fallback: if AI OCR returned nothing, try rule-based extraction on raw text
            if (extracted.isEmpty()) {
                log.info("AI OCR returned empty — falling back to rule-based extraction");
                String rawText = extractRawText(fileBytes, contentType);
                if (rawText != null && !rawText.isBlank()) {
                    extracted = ruleBasedExtractor.extract("", rawText);
                    if (!extracted.isEmpty()) {
                        log.info("Rule-based fallback extracted {} fields", extracted.size());
                    }
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            if (extracted.isEmpty()) {
                response.put("success", false);
                response.put("message", "No data could be extracted. Check API key and quota.");
                response.put("data", extracted);
            } else {
                response.put("success", true);
                response.put("message", "Extraction successful — " + extracted.size() + " fields found");
                response.put("data", extracted);
            }
            response.put("provider", ocrService.getActiveProviderName());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "OCR extraction failed: " + e.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
            ));
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
            // For images, raw text extraction not possible without OCR engine
            // Return empty so caller knows no text was available
            return "";
        } catch (Exception e) {
            log.warn("Failed to extract raw text from file: {}", e.getMessage());
            return "";
        }
    }
}
