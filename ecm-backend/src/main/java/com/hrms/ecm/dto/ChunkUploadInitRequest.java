package com.hrms.ecm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChunkUploadInitRequest {
    @NotBlank
    private String fileName;
    private String contentType;
    @NotNull
    private Long totalSize;
    @NotNull
    private Integer totalChunks;
    @NotNull
    private Long folderId;
}
