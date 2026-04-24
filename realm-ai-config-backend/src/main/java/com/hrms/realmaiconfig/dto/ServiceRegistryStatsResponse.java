package com.hrms.realmaiconfig.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceRegistryStatsResponse {
    private long totalServices;
    private long activeServices;
    private long inactiveServices;
    private long deprecatedServices;
    private List<RegisteredServiceResponse> services;
}
