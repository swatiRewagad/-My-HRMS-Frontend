package com.hrms.cms.service.ocr;

public interface OcrProvider {

    String getProviderName();

    OcrResult extractText(byte[] imageData, String mimeType);
}
