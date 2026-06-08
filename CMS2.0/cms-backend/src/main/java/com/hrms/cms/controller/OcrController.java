package com.hrms.cms.controller;

import com.hrms.cms.service.OcrExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrExtractionService ocrService;

    @GetMapping("/provider")
    public ResponseEntity<Map<String, Object>> getActiveProvider() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "provider", ocrService.getActiveProviderName(),
                "timestamp", LocalDateTime.now().toString()
        ));
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
}
