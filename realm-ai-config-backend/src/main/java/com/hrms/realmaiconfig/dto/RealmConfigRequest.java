package com.hrms.realmaiconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RealmConfigRequest {

    @NotBlank
    private String realmId;

    @NotBlank
    private String mode;

    @NotBlank
    private String platformVersion;

    @NotEmpty
    private List<String> services;

    private Map<String, List<String>> subServices;

    @NotBlank
    private String deploymentType;

    private SchemaConfigDto deploySchema;

    private Map<String, SchemaConfigDto> serviceConfigs;
}
