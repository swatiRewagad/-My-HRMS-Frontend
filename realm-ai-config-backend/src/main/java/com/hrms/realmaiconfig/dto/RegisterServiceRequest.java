package com.hrms.realmaiconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegisterServiceRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String baseUrl;

    private String slug;
    private String category;
    private String version;
    private String description;
    private String healthCheckEndpoint;
    private String authType;
    private String ownerName;
    private String ownerEmail;
    private String tags;
    private String status;
}
