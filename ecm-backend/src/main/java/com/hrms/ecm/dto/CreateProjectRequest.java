package com.hrms.ecm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateProjectRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String description;
}
