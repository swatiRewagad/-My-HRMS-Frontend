package com.hrms.cms.controller;

import com.hrms.cms.service.ocr.OcrResult;
import com.hrms.cms.service.ocr.OcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrService ocrService;

    @PostMapping("/extract")
    public ResponseEntity<OcrResult> extractText(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String mimeType = file.getContentType();
        if (mimeType == null) {
            mimeType = "image/png";
        }

        try {
            byte[] imageData = file.getBytes();
            OcrResult result = ocrService.processImage(imageData, mimeType);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/provider")
    public ResponseEntity<Map<String, String>> getProvider() {
        return ResponseEntity.ok(Map.of("provider", ocrService.getActiveProvider()));
    }
}
