package com.rbi.cms.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Attachment metadata")
public class AttachmentResponse {

    private Long attachmentId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private Instant uploadedAt;
}
