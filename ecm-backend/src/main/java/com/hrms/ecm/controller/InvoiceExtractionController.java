package com.hrms.ecm.controller;

import com.hrms.ecm.dto.InvoiceExtractionDto;
import com.hrms.ecm.service.InvoiceExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoice")
@RequiredArgsConstructor
public class InvoiceExtractionController {

    private final InvoiceExtractionService extractionService;

    @PostMapping("/extract/{fileId}")
    public ResponseEntity<InvoiceExtractionDto> extractInvoice(
            @PathVariable Long fileId,
            @RequestParam(value = "docTypeConfigId", required = false) Long docTypeConfigId) {
        return ResponseEntity.ok(extractionService.extractInvoice(fileId, docTypeConfigId));
    }

    @GetMapping("/extraction/{fileId}")
    public ResponseEntity<InvoiceExtractionDto> getExtraction(@PathVariable Long fileId) {
        return ResponseEntity.ok(extractionService.getExtraction(fileId));
    }
}
