package com.hrms.realmaiconfig.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardResponse {

    private StatsDto stats;
    private List<RealmServiceBarDto> realmServiceBars;
    private List<String> topServices;
    private List<ServiceCategoryDto> serviceCategories;
    private StatusDistributionDto statusDistribution;
    private DeploymentTypesDto deploymentTypes;
    private List<RecentConfigDto> recentConfigs;
    private List<RegisteredServiceDto> registeredServices;
    private List<ActivityDto> recentActivity;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatsDto {
        private long realmsConfigured;
        private long realmsActive;
        private long servicesRegistered;
        private long servicesActive;
        private long deprecatedServices;
        private String platformVersion;
        private String platformReleaseDate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RealmServiceBarDto {
        private String realmName;
        private String realmInitials;
        private long servicesConfigured;
        private int capacity;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceCategoryDto {
        private String category;
        private long count;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatusDistributionDto {
        private long total;
        private long active;
        private long inactive;
        private long deprecated;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DeploymentTypesDto {
        private long independent;
        private long dependent;
        private ModeBreakdownDto modeBreakdown;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ModeBreakdownDto {
        private long appDesigner;
        private long nonAppDesigner;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecentConfigDto {
        private String realmName;
        private String realmInitials;
        private String version;
        private String deployType;
        private String status;
        private String configuredBy;
        private LocalDateTime configuredAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RegisteredServiceDto {
        private String name;
        private String slug;
        private String version;
        private String category;
        private String status;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ActivityDto {
        private String action;
        private String entityType;
        private String entityName;
        private String performedBy;
        private LocalDateTime performedAt;
    }
}
