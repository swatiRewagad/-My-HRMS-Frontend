package com.hrms.realmaiconfig.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegisteredServiceResponse {
    private Long id;
    private String name;
    private String slug;
    private String baseUrl;
    private String version;
    private String description;
    private String category;
    private String authType;
    private String healthCheckEndpoint;
    private String ownerName;
    private String ownerEmail;
    private String tags;
    private String status;
    private LocalDateTime registeredAt;
}
