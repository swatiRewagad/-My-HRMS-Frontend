package com.hrms.realmaiconfig.service;

import com.hrms.realmaiconfig.dto.DashboardResponse;
import com.hrms.realmaiconfig.dto.DashboardResponse.*;
import com.hrms.realmaiconfig.entity.*;
import com.hrms.realmaiconfig.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String PLATFORM_VERSION = "v3.2.1";
    private static final String PLATFORM_RELEASE_DATE = "Released Mar 2026";
    private static final int SERVICE_CAPACITY = 5;

    private final RealmRepository realmRepository;
    private final RealmConfigurationRepository configRepository;
    private final ConfiguredServiceRepository serviceRepository;
    private final RegisteredServiceRepository registeredServiceRepository;
    private final ActivityLogRepository activityLogRepository;

    public DashboardResponse getDashboard() {
        return DashboardResponse.builder()
                .stats(buildStats())
                .realmServiceBars(buildRealmServiceBars())
                .topServices(buildTopServices())
                .serviceCategories(buildServiceCategories())
                .statusDistribution(buildStatusDistribution())
                .deploymentTypes(buildDeploymentTypes())
                .recentConfigs(buildRecentConfigs())
                .registeredServices(buildRegisteredServices())
                .recentActivity(buildRecentActivity())
                .build();
    }

    private StatsDto buildStats() {
        List<RealmConfiguration> activeConfigs = configRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .toList();

        long servicesRegistered = registeredServiceRepository.count();
        long servicesActive = registeredServiceRepository.countByStatus("Active");
        long deprecated = registeredServiceRepository.countByStatus("Deprecated");

        return StatsDto.builder()
                .realmsConfigured(activeConfigs.size())
                .realmsActive(activeConfigs.stream().filter(c -> c.getConfiguredAt() != null).count())
                .servicesRegistered(servicesRegistered)
                .servicesActive(servicesActive)
                .deprecatedServices(deprecated)
                .platformVersion(PLATFORM_VERSION)
                .platformReleaseDate(PLATFORM_RELEASE_DATE)
                .build();
    }

    private List<RealmServiceBarDto> buildRealmServiceBars() {
        List<RealmConfiguration> activeConfigs = configRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .toList();

        Map<String, Realm> realmMap = realmRepository.findAll().stream()
                .collect(Collectors.toMap(Realm::getRealmId, r -> r, (a, b) -> a));

        List<RealmServiceBarDto> bars = new ArrayList<>();
        for (RealmConfiguration config : activeConfigs) {
            Realm realm = realmMap.get(config.getRealmId());
            if (realm == null) continue;

            long svcCount = serviceRepository.findByConfigurationId(config.getId()).size();
            bars.add(RealmServiceBarDto.builder()
                    .realmName(truncate(realm.getDisplayName(), 18))
                    .realmInitials(realm.getInitials())
                    .servicesConfigured(svcCount)
                    .capacity(SERVICE_CAPACITY)
                    .build());
        }
        return bars;
    }

    private List<String> buildTopServices() {
        List<ConfiguredService> allServices = serviceRepository.findAll();
        return allServices.stream()
                .collect(Collectors.groupingBy(ConfiguredService::getServiceId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(4)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<ServiceCategoryDto> buildServiceCategories() {
        List<RegisteredService> all = registeredServiceRepository.findAll();
        return all.stream()
                .filter(s -> s.getCategory() != null)
                .collect(Collectors.groupingBy(RegisteredService::getCategory, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> ServiceCategoryDto.builder()
                        .category(e.getKey())
                        .count(e.getValue())
                        .build())
                .toList();
    }

    private StatusDistributionDto buildStatusDistribution() {
        long total = registeredServiceRepository.count();
        long active = registeredServiceRepository.countByStatus("Active");
        long inactive = registeredServiceRepository.countByStatus("Inactive");
        long deprecated = registeredServiceRepository.countByStatus("Deprecated");

        return StatusDistributionDto.builder()
                .total(total)
                .active(active)
                .inactive(inactive)
                .deprecated(deprecated)
                .build();
    }

    private DeploymentTypesDto buildDeploymentTypes() {
        List<RealmConfiguration> activeConfigs = configRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .toList();

        long independent = activeConfigs.stream().filter(c -> "independent".equalsIgnoreCase(c.getDeploymentType())).count();
        long dependent = activeConfigs.stream().filter(c -> "dependent".equalsIgnoreCase(c.getDeploymentType())).count();
        long appDesigner = activeConfigs.stream().filter(c -> "app-designer".equalsIgnoreCase(c.getMode())).count();
        long nonAppDesigner = activeConfigs.stream().filter(c -> "non-app-designer".equalsIgnoreCase(c.getMode())).count();

        return DeploymentTypesDto.builder()
                .independent(independent)
                .dependent(dependent)
                .modeBreakdown(ModeBreakdownDto.builder()
                        .appDesigner(appDesigner)
                        .nonAppDesigner(nonAppDesigner)
                        .build())
                .build();
    }

    private List<RecentConfigDto> buildRecentConfigs() {
        Map<String, Realm> realmMap = realmRepository.findAll().stream()
                .collect(Collectors.toMap(Realm::getRealmId, r -> r, (a, b) -> a));

        return configRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .sorted(Comparator.comparing(RealmConfiguration::getConfiguredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(config -> {
                    Realm realm = realmMap.get(config.getRealmId());
                    String realmName = realm != null ? realm.getDisplayName() : config.getRealmId();
                    String initials = realm != null ? realm.getInitials() : config.getRealmId().substring(0, 2).toUpperCase();

                    return RecentConfigDto.builder()
                            .realmName(realmName)
                            .realmInitials(initials)
                            .version(config.getPlatformVersion())
                            .deployType(config.getDeploymentType())
                            .status("Active")
                            .configuredBy(config.getConfiguredBy())
                            .configuredAt(config.getConfiguredAt())
                            .build();
                })
                .toList();
    }

    private List<RegisteredServiceDto> buildRegisteredServices() {
        return registeredServiceRepository.findAll().stream()
                .sorted(Comparator.comparing(RegisteredService::getRegisteredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(svc -> RegisteredServiceDto.builder()
                        .name(svc.getName())
                        .slug(svc.getSlug())
                        .version(svc.getVersion())
                        .category(svc.getCategory())
                        .status(svc.getStatus())
                        .build())
                .toList();
    }

    private List<ActivityDto> buildRecentActivity() {
        return activityLogRepository.findTop10ByOrderByPerformedAtDesc().stream()
                .map(log -> ActivityDto.builder()
                        .action(log.getAction())
                        .entityType(log.getEntityType())
                        .entityName(log.getEntityName())
                        .performedBy(log.getPerformedBy())
                        .performedAt(log.getPerformedAt())
                        .build())
                .toList();
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }
}
