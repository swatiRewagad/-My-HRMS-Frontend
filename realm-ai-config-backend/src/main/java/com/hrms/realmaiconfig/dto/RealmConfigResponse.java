package com.hrms.realmaiconfig.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RealmConfigResponse {
    private Long id;
    private String realmId;
    private String mode;
    private String platformVersion;
    private String deploymentType;
    private String configuredBy;
    private LocalDateTime configuredAt;
    private List<ServiceResponse> services;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceResponse {
        private String serviceId;
        private String serviceLabel;
        private String serviceGroup;
        private List<String> subServices;
    }
}
