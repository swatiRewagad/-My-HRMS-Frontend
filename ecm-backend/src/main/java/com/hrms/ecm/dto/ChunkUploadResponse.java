package com.hrms.ecm.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChunkUploadResponse {
    private String uploadId;
    private Integer chunksReceived;
    private Integer totalChunks;
    private String status;
    private FileDto file;
}
