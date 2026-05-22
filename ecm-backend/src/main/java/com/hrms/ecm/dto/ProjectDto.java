package com.hrms.ecm.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private String createdByName;
    private LocalDateTime createdAt;
    private UploadConfigDto uploadConfig;
    private List<DocumentTypeConfigDto> documentTypes;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UploadConfigDto {
        private Long id;
        private Long maxFileSizeBytes;
        private String maxFileSizeFormatted;
        private Long totalAllocatedStorageBytes;
        private String totalAllocatedStorageFormatted;
        private String allowedContentTypes;
        private String uploadBasePath;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DocumentTypeConfigDto {
        private Long id;
        private Long projectId;
        private String typeName;
        private String typeCode;
        private String description;
        private Boolean extractionEnabled;
        private String status;
        private List<InvoiceFieldDto> extractionFields;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InvoiceFieldDto {
        private Long id;
        private Long docTypeConfigId;
        private String fieldName;
        private String fieldKey;
        private String fieldType;
        private Boolean required;
        private Integer displayOrder;
        private String fieldCategory;
    }
}
