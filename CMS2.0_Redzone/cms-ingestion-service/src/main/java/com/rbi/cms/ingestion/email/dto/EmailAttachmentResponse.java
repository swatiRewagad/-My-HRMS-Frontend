package com.rbi.cms.ingestion.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachmentResponse {

    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String ocrText;
    private Double ocrConfidence;
    private Instant createdAt;
}
