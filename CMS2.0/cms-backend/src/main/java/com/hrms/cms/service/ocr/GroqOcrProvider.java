package com.hrms.cms.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Component
@Slf4j
public class GroqOcrProvider implements OcrProvider {

    @Value("${cms.ocr.groq-api-key:}")
    private String apiKey;

    @Value("${cms.ocr.groq-model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Override
    public String getProviderName() { return "groq"; }

    @Override
    public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public Map<String, String> extractFields(byte[] fileBytes, String mimeType) {

        try {
            if ("application/pdf".equals(mimeType)) {
                // First try text-based extraction
                Map<String, String> textResult = extractFromPdfAsText(fileBytes);
                if (!textResult.isEmpty()) {
                    return textResult;
                }
                // PDF is image-based — render first page as image and use vision API
                log.info("PDF text extraction empty, falling back to vision API (render page 1 as image)");
                byte[] pageImage = renderFirstPageAsImage(fileBytes);
                if (pageImage != null) {
                    String base64Data = Base64.getEncoder().encodeToString(pageImage);
                    String dataUrl = "data:image/jpeg;base64," + base64Data;
                    Map<String, Object> requestBody = buildRequest(dataUrl);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(apiKey);
                    try {
                        ResponseEntity<String> response = restTemplate.exchange(
                                GROQ_URL, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);
                        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                            return parseResponse(response.getBody());
                        }
                    } catch (org.springframework.web.client.HttpClientErrorException e) {
                        String errorBody = e.getResponseBodyAsString();
                        log.warn("Groq vision 400 on PDF image — salvaging. Snippet: {}",
                                errorBody.length() > 200 ? errorBody.substring(0, 200) : errorBody);
                        Map<String, String> salvaged = salvageFailedGeneration(errorBody);
                        if (!salvaged.isEmpty()) return salvaged;
                    }
                }
                return Collections.emptyMap();
            }

            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64Data;

            Map<String, Object> requestBody = buildRequest(dataUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        GROQ_URL, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return parseResponse(response.getBody());
                }
                log.error("Groq returned: {}", response.getStatusCode());
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // Groq returns 400 json_validate_failed when the model produces imperfect JSON.
                // The error body contains a "failed_generation" field with the partial output — try to salvage it.
                String errorBody = e.getResponseBodyAsString();
                log.warn("Groq 400 error — attempting to salvage failed_generation. Body snippet: {}",
                        errorBody.length() > 300 ? errorBody.substring(0, 300) : errorBody);
                Map<String, String> salvaged = salvageFailedGeneration(errorBody);
                if (!salvaged.isEmpty()) {
                    log.info("Salvaged {} fields from failed_generation", salvaged.size());
                    return salvaged;
                }
            }
            return Collections.emptyMap();

        } catch (Exception e) {
            log.error("Groq OCR failed: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private Map<String, String> extractFromPdfAsText(byte[] pdfBytes) {
        // Extract raw text from PDF using basic parsing
        String pdfText = extractTextFromPdf(pdfBytes);
        if (pdfText.isBlank()) {
            log.warn("Could not extract text from PDF");
            return Collections.emptyMap();
        }

        log.info("Extracted {} chars from PDF, sending to Groq for structuring", pdfText.length());

        // Send extracted text to Groq for field extraction
        Map<String, Object> systemMsg = Map.of(
                "role", "system",
                "content", "You are a JSON-only extraction assistant. You MUST respond with ONLY a valid JSON object. No markdown, no code fences, no explanation text. Start your response with '{' and end with '}'."
        );

        Map<String, Object> textContent = Map.of("type", "text",
                "text", OcrPrompts.EXTRACTION_PROMPT + "\n\nHere is the text from the complaint letter:\n\n" + pdfText);

        Map<String, Object> userMsg = Map.of("role", "user", "content", List.of(textContent));

        // Do NOT use response_format:json_object here — Groq's strict JSON validator
        // rejects the output with 400 json_validate_failed for multilingual/complex docs.
        // We handle imperfect JSON ourselves in OcrResponseParser + salvageFailedGeneration.
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(systemMsg, userMsg),
                "temperature", 0.1,
                "max_tokens", 2048
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    GROQ_URL, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Groq 400 json_validate_failed — salvage whatever it generated
            String errorBody = e.getResponseBodyAsString();
            log.warn("Groq 400 on PDF text extraction — salvaging failed_generation. Snippet: {}",
                    errorBody.length() > 200 ? errorBody.substring(0, 200) : errorBody);
            Map<String, String> salvaged = salvageFailedGeneration(errorBody);
            if (!salvaged.isEmpty()) {
                log.info("Salvaged {} fields from failed PDF text extraction", salvaged.size());
                return salvaged;
            }
        } catch (Exception e) {
            log.error("Groq text extraction failed: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    private String extractTextFromPdf(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc).trim();
            log.info("PDFBox extracted {} chars from PDF ({} pages)", text.length(), doc.getNumberOfPages());
            if (text.length() < 20) {
                log.warn("PDF appears to be image-based — insufficient text extracted ({})", text.length());
                return "";
            }
            return text;
        } catch (Exception e) {
            log.error("PDFBox text extraction failed: {}", e.getMessage());
            return "";
        }
    }

    private byte[] renderFirstPageAsImage(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();
            // Use lower DPI for many pages to keep image size reasonable for API
            int dpi = pageCount <= 2 ? 200 : (pageCount <= 4 ? 150 : 120);

            if (pageCount == 1) {
                BufferedImage image = renderer.renderImageWithDPI(0, dpi);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpeg", baos);
                log.info("Rendered PDF page 1 as JPEG image ({} bytes)", baos.size());
                return baos.toByteArray();
            }

            // Render all pages and stitch vertically so AI can see entire document
            BufferedImage[] pages = new BufferedImage[pageCount];
            int totalHeight = 0;
            int maxWidth = 0;
            for (int i = 0; i < pageCount; i++) {
                pages[i] = renderer.renderImageWithDPI(i, dpi);
                totalHeight += pages[i].getHeight();
                maxWidth = Math.max(maxWidth, pages[i].getWidth());
            }

            BufferedImage combined = new BufferedImage(maxWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
            var g = combined.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, maxWidth, totalHeight);
            int yOffset = 0;
            for (int i = 0; i < pageCount; i++) {
                g.drawImage(pages[i], 0, yOffset, null);
                yOffset += pages[i].getHeight();
            }
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(combined, "jpeg", baos);
            log.info("Rendered all {} PDF pages as combined JPEG image ({} bytes, {}x{} px)",
                    pageCount, baos.size(), maxWidth, totalHeight);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render PDF page as image: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildRequest(String dataUrl) {
        Map<String, Object> systemMsg = Map.of(
                "role", "system",
                "content", "You are a JSON-only extraction assistant. You MUST respond with ONLY a valid JSON object. No markdown, no code fences, no explanation text. Start your response with '{' and end with '}'."
        );

        Map<String, Object> textContent = Map.of("type", "text", "text", OcrPrompts.EXTRACTION_PROMPT);
        Map<String, Object> imageUrl = Map.of("url", dataUrl);
        Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrl);

        Map<String, Object> userMsg = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );

        // Note: no response_format here — vision models sometimes produce imperfect JSON
        // which triggers Groq's 400 json_validate_failed; we handle that with salvageFailedGeneration()
        return Map.of(
                "model", model,
                "messages", List.of(systemMsg, userMsg),
                "temperature", 0.1,
                "max_tokens", 2048
        );
    }

    private Map<String, String> parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("choices").get(0).path("message").path("content").asText("");
        log.info("Groq raw LLM response (first 500 chars): {}", text.length() > 500 ? text.substring(0, 500) : text);
        return OcrResponseParser.parseJsonFromLlmResponse(text, objectMapper);
    }

    private Map<String, String> salvageFailedGeneration(String errorBody) {
        try {
            JsonNode root = objectMapper.readTree(errorBody);
            String partial = root.path("error").path("failed_generation").asText("");
            if (partial.isBlank()) return Collections.emptyMap();
            log.info("Salvaging failed_generation ({} chars)", partial.length());
            return OcrResponseParser.parseJsonFromLlmResponse(partial, objectMapper);
        } catch (Exception e) {
            log.warn("Could not salvage failed_generation: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
