package com.hrms.cms.service.ocr;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {
    private String rawText;
    private String language;
    private int confidence;
    private String provider;
}
