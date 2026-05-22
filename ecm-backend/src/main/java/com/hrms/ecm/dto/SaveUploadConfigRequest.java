package com.hrms.ecm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaveUploadConfigRequest {
    @NotNull
    private Long maxFileSizeMb;
    @NotNull
    private Long totalAllocatedStorageGb;
    @NotBlank
    private String allowedContentTypes;
    @NotBlank
    private String uploadBasePath;
}
