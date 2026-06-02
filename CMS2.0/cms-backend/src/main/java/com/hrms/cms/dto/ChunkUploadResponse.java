package com.hrms.cms.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkUploadResponse {
    private String uploadId;
    private int chunkIndex;
    private int totalChunks;
    private boolean complete;
    private Long attachmentId;
    private String fileName;
    private String storagePath;
    private String message;
}
