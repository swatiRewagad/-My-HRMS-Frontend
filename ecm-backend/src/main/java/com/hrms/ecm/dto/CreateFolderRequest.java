package com.hrms.ecm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateFolderRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String visibility;
    private String description;
    private Long parentId;
}
