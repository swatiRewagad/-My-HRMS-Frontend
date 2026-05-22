package com.hrms.ecm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaveDocTypeRequest {
    @NotBlank
    private String typeName;
    @NotBlank
    private String typeCode;
    private String description;
    private Boolean extractionEnabled;
    private List<FieldEntry> extractionFields;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FieldEntry {
        private String fieldName;
        private String fieldKey;
        private String fieldType;
        private Boolean required;
        private Integer displayOrder;
        private String fieldCategory;
    }
}
