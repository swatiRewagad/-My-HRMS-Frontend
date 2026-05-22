package com.hrms.cms.service.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OcrService {

    private final OcrProvider provider;

    public OcrService(OcrProvider provider) {
        this.provider = provider;
        log.info("OCR Service initialized with provider: {}", provider.getProviderName());
    }

    public OcrResult processImage(byte[] imageData, String mimeType) {
        log.info("Processing image with provider: {}, size: {} bytes", provider.getProviderName(), imageData.length);
        return provider.extractText(imageData, mimeType);
    }

    public String getActiveProvider() {
        return provider.getProviderName();
    }
}
