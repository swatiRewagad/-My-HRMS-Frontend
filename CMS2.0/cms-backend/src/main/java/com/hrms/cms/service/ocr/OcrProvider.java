package com.hrms.cms.service.ocr;

import java.util.Map;

/**
 * Strategy interface for OCR extraction providers.
 * Implement this to add a new OCR provider (Gemini, OpenAI, Tesseract, HuggingFace, Azure, etc.)
 */
public interface OcrProvider {

    String getProviderName();

    Map<String, String> extractFields(byte[] fileBytes, String mimeType);
}
