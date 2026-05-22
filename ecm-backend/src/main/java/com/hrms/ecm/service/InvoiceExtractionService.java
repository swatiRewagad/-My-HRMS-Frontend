package com.hrms.ecm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.ecm.dto.InvoiceExtractionDto;
import com.hrms.ecm.entity.*;
import com.hrms.ecm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceExtractionService {

    private final FileRepository fileRepo;
    private final InvoiceExtractionRepository extractionRepo;
    private final InvoiceFieldConfigRepository fieldConfigRepo;
    private final DocumentTypeConfigRepository docTypeRepo;
    private final ObjectMapper objectMapper;

    @Value("${ecm.llm.api-key:}")
    private String llmApiKey;

    @Value("${ecm.llm.api-url:https://api.anthropic.com/v1/messages}")
    private String llmApiUrl;

    @Value("${ecm.llm.model:claude-sonnet-4-6-20250514}")
    private String llmModel;

    public InvoiceExtractionDto extractInvoice(Long fileId, Long docTypeConfigId) {
        FileEntity file = fileRepo.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!"application/pdf".equals(file.getContentType())) {
            throw new RuntimeException("Only PDF files are supported for invoice extraction");
        }

        Optional<InvoiceExtraction> existing = extractionRepo.findByFileId(fileId);
        if (existing.isPresent() && "completed".equals(existing.get().getStatus())) {
            return toDto(existing.get(), file.getOriginalName(), docTypeConfigId);
        }

        List<InvoiceFieldConfig> configuredFields = List.of();
        if (docTypeConfigId != null) {
            configuredFields = fieldConfigRepo.findByDocTypeConfigIdOrderByDisplayOrderAsc(docTypeConfigId);
        }

        String rawText;
        try {
            rawText = extractTextFromPdf(file.getStoragePath());
        } catch (IOException e) {
            log.error("Failed to read PDF: {}", e.getMessage());
            InvoiceExtraction failed = InvoiceExtraction.builder()
                    .fileId(fileId).status("error")
                    .errorMessage("Failed to read PDF: " + e.getMessage())
                    .build();
            extractionRepo.save(failed);
            return toDto(failed, file.getOriginalName(), docTypeConfigId);
        }

        boolean isScanned = rawText.trim().isEmpty();

        if (isScanned) {
            log.info("No embedded text found — using Tesseract OCR for scanned PDF");
            try {
                rawText = ocrPdfPages(file.getStoragePath());
            } catch (Exception e) {
                log.error("OCR failed: {}", e.getMessage());
                InvoiceExtraction failed = InvoiceExtraction.builder()
                        .fileId(fileId).status("error").rawText("")
                        .errorMessage("OCR failed: " + e.getMessage())
                        .build();
                extractionRepo.save(failed);
                return toDto(failed, file.getOriginalName(), docTypeConfigId);
            }

            if (rawText.trim().isEmpty()) {
                InvoiceExtraction failed = InvoiceExtraction.builder()
                        .fileId(fileId).status("error").rawText("")
                        .errorMessage("OCR could not extract any text from the scanned PDF.")
                        .build();
                extractionRepo.save(failed);
                return toDto(failed, file.getOriginalName(), docTypeConfigId);
            }
        }

        try {
            InvoiceExtraction extraction = parseWithLlm(fileId, rawText, configuredFields);
            extraction = extractionRepo.save(extraction);
            return toDto(extraction, file.getOriginalName(), docTypeConfigId);
        } catch (Exception e) {
            log.error("LLM extraction failed, falling back to regex: {}", e.getMessage());
            InvoiceExtraction extraction = parseWithRegex(fileId, rawText);
            extraction = extractionRepo.save(extraction);
            return toDto(extraction, file.getOriginalName(), docTypeConfigId);
        }
    }

    public InvoiceExtractionDto getExtraction(Long fileId) {
        FileEntity file = fileRepo.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
        InvoiceExtraction extraction = extractionRepo.findByFileId(fileId)
                .orElseThrow(() -> new RuntimeException("No extraction found for this file"));
        return toDto(extraction, file.getOriginalName(), null);
    }

    private String extractTextFromPdf(String storagePath) throws IOException {
        Path path = Paths.get(storagePath);
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String ocrPdfPages(String storagePath) throws IOException, TesseractException {
        Path path = Paths.get(storagePath);
        StringBuilder ocrText = new StringBuilder();

        Tesseract tesseract = new Tesseract();
        tesseract.setOcrEngineMode(1);   // OEM_LSTM_ONLY — highest accuracy neural net
        tesseract.setPageSegMode(3);     // PSM_AUTO — fully automatic page segmentation
        tesseract.setLanguage("eng");
        tesseract.setVariable("user_defined_dpi", "300");
        tesseract.setVariable("preserve_interword_spaces", "1");
        tesseract.setVariable("textord_heavy_nr", "1");

        // tess4j setDatapath points to the folder that CONTAINS eng.traineddata directly
        String tempDir = System.getProperty("java.io.tmpdir");
        java.io.File tessdataDir = new java.io.File(tempDir, "tess4j-data");
        if (!tessdataDir.exists()) {
            tessdataDir.mkdirs();
        }
        java.io.File engData = new java.io.File(tessdataDir, "eng.traineddata");
        if (!engData.exists() || engData.length() < 100_000) {
            try (var is = getClass().getClassLoader().getResourceAsStream("tessdata/eng.traineddata")) {
                if (is != null) {
                    Files.copy(is, engData.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.info("Extracted bundled eng.traineddata ({} bytes) to {}", engData.length(), engData.getAbsolutePath());
                }
            }
        }
        tesseract.setDatapath(tessdataDir.getAbsolutePath());

        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(path))) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int maxPages = Math.min(doc.getNumberOfPages(), 10);
            for (int i = 0; i < maxPages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300, ImageType.GRAY);
                String pageText = tesseract.doOCR(image);
                if (pageText != null && !pageText.isBlank()) {
                    ocrText.append(pageText).append("\n");
                }
            }
        }

        log.info("OCR extracted {} characters from scanned PDF", ocrText.length());
        return ocrText.toString();
    }

    private InvoiceExtraction parseWithLlm(Long fileId, String rawText, List<InvoiceFieldConfig> configuredFields) throws Exception {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            throw new RuntimeException("LLM API key not configured");
        }

        String truncatedText = rawText.length() > 8000 ? rawText.substring(0, 8000) : rawText;
        String prompt = buildPrompt(truncatedText, configuredFields);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", llmModel,
                "max_tokens", 4096,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        ));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(llmApiUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", llmApiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API returned status " + response.statusCode() + ": " + response.body());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        String content = responseJson.get("content").get(0).get("text").asText();

        String jsonContent = content.trim();
        if (jsonContent.startsWith("```")) {
            jsonContent = jsonContent.replaceAll("```json?\\s*", "").replaceAll("```\\s*$", "").trim();
        }

        JsonNode parsed = objectMapper.readTree(jsonContent);

        String lineItemsStr = "[]";
        if (parsed.has("lineItems") && parsed.get("lineItems").isArray()) {
            lineItemsStr = objectMapper.writeValueAsString(parsed.get("lineItems"));
        }

        return InvoiceExtraction.builder()
                .fileId(fileId)
                .invoiceNumber(getField(parsed, "invoiceNumber"))
                .invoiceDate(getField(parsed, "invoiceDate"))
                .dueDate(getField(parsed, "dueDate"))
                .vendorName(getField(parsed, "vendorName"))
                .vendorAddress(getField(parsed, "vendorAddress"))
                .customerName(getField(parsed, "customerName"))
                .customerAddress(getField(parsed, "customerAddress"))
                .subtotal(getField(parsed, "subtotal"))
                .tax(getField(parsed, "tax"))
                .totalAmount(getField(parsed, "totalAmount"))
                .currency(getField(parsed, "currency"))
                .lineItemsJson(lineItemsStr)
                .rawText(truncatedText)
                .status("completed")
                .build();
    }

    private String buildPrompt(String text, List<InvoiceFieldConfig> configuredFields) {
        if (configuredFields == null || configuredFields.isEmpty()) {
            return buildDefaultPrompt(text);
        }

        List<InvoiceFieldConfig> headerFields = configuredFields.stream()
                .filter(f -> !"lineItem".equals(f.getFieldCategory()))
                .collect(Collectors.toList());
        List<InvoiceFieldConfig> lineItemFields = configuredFields.stream()
                .filter(f -> "lineItem".equals(f.getFieldCategory()))
                .collect(Collectors.toList());

        StringBuilder jsonStructure = new StringBuilder("{\n");
        for (InvoiceFieldConfig hf : headerFields) {
            jsonStructure.append("  \"").append(hf.getFieldKey()).append("\": \"\",  // ")
                    .append(hf.getFieldName())
                    .append(Boolean.TRUE.equals(hf.getRequired()) ? " (REQUIRED)" : "")
                    .append("\n");
        }

        if (!lineItemFields.isEmpty()) {
            jsonStructure.append("  \"lineItems\": [\n    {\n");
            for (InvoiceFieldConfig lf : lineItemFields) {
                jsonStructure.append("      \"").append(lf.getFieldKey()).append("\": \"\",  // ")
                        .append(lf.getFieldName())
                        .append(Boolean.TRUE.equals(lf.getRequired()) ? " (REQUIRED)" : "")
                        .append("\n");
            }
            jsonStructure.append("    }\n  ]\n");
        }
        jsonStructure.append("}");

        return "You are an invoice data extraction assistant. Analyze the following text extracted from a PDF invoice.\n\n" +
                "Extract ONLY the fields defined below. Return ONLY valid JSON (no markdown, no code fences).\n\n" +
                "Required JSON structure:\n" + jsonStructure + "\n\n" +
                "Rules:\n" +
                "- Extract all fields you can find. Leave empty string for missing fields.\n" +
                "- Fields marked REQUIRED should be prioritized.\n" +
                "- For lineItems, extract every row from the invoice table.\n" +
                "- Amounts should be numeric values only (no currency symbols).\n" +
                "- Dates should be in the format found in the document.\n\n" +
                "Invoice text:\n" + text;
    }

    private String buildDefaultPrompt(String text) {
        return """
                You are an invoice data extraction assistant. Analyze the following text extracted from a PDF invoice and extract structured data.

                Return ONLY valid JSON with this exact structure (no markdown, no code fences):
                {
                  "invoiceNumber": "",
                  "invoiceDate": "",
                  "dueDate": "",
                  "vendorName": "",
                  "vendorAddress": "",
                  "customerName": "",
                  "customerAddress": "",
                  "subtotal": "",
                  "tax": "",
                  "totalAmount": "",
                  "currency": "",
                  "lineItems": [
                    {
                      "slNo": 1,
                      "description": "",
                      "quantity": "",
                      "unitPrice": "",
                      "amount": "",
                      "hsnCode": "",
                      "taxRate": ""
                    }
                  ]
                }

                Rules:
                - Extract all fields you can find. Leave empty string for missing fields.
                - For lineItems, extract every row from the invoice table.
                - slNo is the serial/line number (integer).
                - Amounts should include the numeric value only (no currency symbols).
                - Currency should be the 3-letter code (INR, USD, EUR, etc.) or symbol found.

                Invoice text:
                """ + text;
    }

    private InvoiceExtraction parseWithRegex(Long fileId, String rawText) {
        String truncatedText = rawText.length() > 8000 ? rawText.substring(0, 8000) : rawText;
        String textUpper = rawText.toUpperCase();

        // Invoice number: look for "Invoice Number" or "Invoice No" or "Invoice #" followed by digits
        String invoiceNumber = extractPattern(rawText, "(?i)invoice\\s*(?:number|no\\.?|#)\\s*:?\\s*([A-Z0-9][A-Z0-9\\-/]*)");
        if (invoiceNumber.isEmpty()) {
            invoiceNumber = extractPattern(rawText, "(?i)inv\\.?\\s*(?:no\\.?|#)\\s*:?\\s*([A-Z0-9][A-Z0-9\\-/]*)");
        }

        // Date: look for "Date" preceded by nothing or "Invoice", skip "Due Date"
        String invoiceDate = extractPattern(rawText, "(?i)(?:invoice\\s+)?date\\s*:?\\s*(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4})");
        if (invoiceDate.isEmpty()) {
            invoiceDate = extractPattern(rawText, "(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4})");
        }

        // Vendor: look for company name patterns (Pty Ltd, Inc, LLC, Corp, Ltd)
        String vendorName = extractPattern(rawText, "(?i)([A-Z][A-Za-z\\s&]+(?:Pty\\s+Ltd|Inc|LLC|Corp|Ltd|Limited|Co\\.?))");

        // Customer: look for "Bill To" section
        String customerName = extractPattern(rawText, "(?i)bill\\s*to\\s*:?\\s*\\n?\\s*([A-Z][A-Za-z\\s&]+(?:Pty\\s+Ltd|Inc|LLC|Corp|Ltd|Limited|Co\\.?))");

        // Total: look for "Total" at end, not "Subtotal" or "Sale Amount"
        String totalAmount = extractPattern(rawText, "(?i)(?<!sub\\s?)(?<!sale\\s)total\\s*:?\\s*[\\$\\u20B9€£]?\\s*([\\d,]+\\.\\d{2})");

        // Tax/GST
        String tax = extractPattern(rawText, "(?i)(?:GST|tax|vat)\\s*:?\\s*[\\$\\u20B9€£]?\\s*([\\d,]+\\.\\d{2})");

        // Subtotal / Sale Amount
        String subtotal = extractPattern(rawText, "(?i)(?:sub\\s*-?\\s*total|sale\\s+amount)\\s*:?\\s*[\\$\\u20B9€£]?\\s*([\\d,]+\\.\\d{2})");

        // Currency detection
        String currency = "";
        if (textUpper.contains("INR") || rawText.contains("₹")) currency = "INR";
        else if (rawText.contains("$")) currency = "AUD";
        else if (textUpper.contains("USD")) currency = "USD";
        else if (rawText.contains("€") || textUpper.contains("EUR")) currency = "EUR";
        else if (rawText.contains("£") || textUpper.contains("GBP")) currency = "GBP";

        // Line items: try to extract table rows with Qty, Description, Price, Amount pattern
        List<Map<String, Object>> lineItems = extractLineItems(rawText);
        String lineItemsJson;
        try {
            lineItemsJson = objectMapper.writeValueAsString(lineItems);
        } catch (Exception e) {
            lineItemsJson = "[]";
        }

        return InvoiceExtraction.builder()
                .fileId(fileId)
                .invoiceNumber(invoiceNumber)
                .invoiceDate(invoiceDate)
                .dueDate(extractPattern(rawText, "(?i)due\\s*date\\s*:?\\s*(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4})"))
                .vendorName(vendorName)
                .customerName(customerName)
                .totalAmount(totalAmount)
                .tax(tax)
                .subtotal(subtotal)
                .currency(currency)
                .lineItemsJson(lineItemsJson)
                .rawText(truncatedText)
                .status("completed")
                .errorMessage("Extracted using OCR + pattern matching. Some fields may need manual verification.")
                .build();
    }

    private List<Map<String, Object>> extractLineItems(String rawText) {
        List<Map<String, Object>> items = new ArrayList<>();
        // Match lines like: <qty> <code> <description> <price> <amount> <tax_code>
        var pattern = java.util.regex.Pattern.compile(
                "(?m)^\\s*(\\d+)\\s+([A-Z][A-Z0-9]*)?\\s+(.+?)\\s+\\$?([\\d,]+\\.\\d{2})\\s+.*?\\$?([\\d,]+\\.\\d{2})\\s*(GST|VAT|TAX)?");
        var matcher = pattern.matcher(rawText);
        int slNo = 1;
        while (matcher.find()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("slNo", slNo++);
            item.put("description", matcher.group(3).trim());
            item.put("quantity", matcher.group(1));
            item.put("unitPrice", matcher.group(4));
            item.put("amount", matcher.group(5));
            item.put("hsnCode", matcher.group(2) != null ? matcher.group(2) : "");
            item.put("taxRate", matcher.group(6) != null ? matcher.group(6) : "");
            items.add(item);
        }
        return items;
    }

    private String extractPattern(String text, String regex) {
        var matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String getField(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : "";
    }

    private InvoiceExtractionDto toDto(InvoiceExtraction e, String fileName, Long docTypeConfigId) {
        List<InvoiceExtractionDto.LineItem> lineItems = new ArrayList<>();
        if (e.getLineItemsJson() != null && !e.getLineItemsJson().isBlank()) {
            try {
                lineItems = objectMapper.readValue(e.getLineItemsJson(),
                        new TypeReference<List<InvoiceExtractionDto.LineItem>>() {});
            } catch (Exception ignored) {}
        }

        List<InvoiceExtractionDto.ConfiguredField> fieldDefs = new ArrayList<>();
        if (docTypeConfigId != null) {
            List<InvoiceFieldConfig> configs = fieldConfigRepo.findByDocTypeConfigIdOrderByDisplayOrderAsc(docTypeConfigId);
            for (InvoiceFieldConfig fc : configs) {
                fieldDefs.add(InvoiceExtractionDto.ConfiguredField.builder()
                        .fieldName(fc.getFieldName())
                        .fieldKey(fc.getFieldKey())
                        .fieldType(fc.getFieldType())
                        .required(fc.getRequired())
                        .fieldCategory(fc.getFieldCategory())
                        .displayOrder(fc.getDisplayOrder())
                        .build());
            }
        }

        return InvoiceExtractionDto.builder()
                .id(e.getId())
                .fileId(e.getFileId())
                .fileName(fileName)
                .invoiceNumber(e.getInvoiceNumber())
                .invoiceDate(e.getInvoiceDate())
                .dueDate(e.getDueDate())
                .vendorName(e.getVendorName())
                .vendorAddress(e.getVendorAddress())
                .customerName(e.getCustomerName())
                .customerAddress(e.getCustomerAddress())
                .subtotal(e.getSubtotal())
                .tax(e.getTax())
                .totalAmount(e.getTotalAmount())
                .currency(e.getCurrency())
                .lineItems(lineItems)
                .configuredFields(fieldDefs)
                .status(e.getStatus())
                .errorMessage(e.getErrorMessage())
                .extractedAt(e.getExtractedAt())
                .build();
    }
}
