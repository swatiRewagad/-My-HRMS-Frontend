package com.hrms.realmaiconfig.service;

import com.hrms.realmaiconfig.dto.*;
import com.hrms.realmaiconfig.entity.*;
import com.hrms.realmaiconfig.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RealmConfigService {

    private final RealmRepository realmRepository;
    private final RealmConfigurationRepository configRepository;
    private final ConfiguredServiceRepository serviceRepository;
    private final ConfiguredSubServiceRepository subServiceRepository;

    public List<Realm> getActiveRealms() {
        return realmRepository.findByStatus("active");
    }

    public Optional<RealmConfigResponse> getConfigurationByRealmId(String realmId) {
        return configRepository.findByRealmIdAndIsActiveTrue(realmId)
                .map(this::mapToResponse);
    }

    @Transactional
    public RealmConfigResponse saveConfiguration(RealmConfigRequest request) {
        configRepository.findByRealmIdAndIsActiveTrue(request.getRealmId())
                .ifPresent(existing -> {
                    List<ConfiguredService> oldServices = serviceRepository.findByConfigurationId(existing.getId());
                    List<Long> oldServiceIds = oldServices.stream().map(ConfiguredService::getId).toList();
                    if (!oldServiceIds.isEmpty()) {
                        subServiceRepository.deleteByConfiguredServiceIdIn(oldServiceIds);
                    }
                    serviceRepository.deleteByConfigurationId(existing.getId());
                    existing.setIsActive(false);
                    configRepository.save(existing);
                });

        RealmConfiguration config = RealmConfiguration.builder()
                .realmId(request.getRealmId())
                .mode(request.getMode())
                .platformVersion(request.getPlatformVersion())
                .deploymentType(request.getDeploymentType())
                .configuredBy("System User")
                .build();

        if (request.getDeploySchema() != null) {
            config.setDeploySchemaName(request.getDeploySchema().getSchemaName());
            config.setDeployDbType(request.getDeploySchema().getDbType());
            config.setDeployConnectionString(request.getDeploySchema().getConnectionString());
            config.setDeploySecretPath(request.getDeploySchema().getSecretPath());
        }

        config = configRepository.save(config);

        for (String serviceId : request.getServices()) {
            ConfiguredService svc = ConfiguredService.builder()
                    .configurationId(config.getId())
                    .serviceId(serviceId)
                    .serviceLabel(serviceId)
                    .build();

            if (request.getServiceConfigs() != null && request.getServiceConfigs().containsKey(serviceId)) {
                SchemaConfigDto svcConfig = request.getServiceConfigs().get(serviceId);
                svc.setSchemaName(svcConfig.getSchemaName());
                svc.setDbType(svcConfig.getDbType());
                svc.setConnectionString(svcConfig.getConnectionString());
                svc.setSecretPath(svcConfig.getSecretPath());
            }

            svc = serviceRepository.save(svc);

            if (request.getSubServices() != null && request.getSubServices().containsKey(serviceId)) {
                for (String subId : request.getSubServices().get(serviceId)) {
                    ConfiguredSubService sub = ConfiguredSubService.builder()
                            .configuredServiceId(svc.getId())
                            .subServiceId(subId)
                            .subServiceLabel(subId)
                            .build();
                    subServiceRepository.save(sub);
                }
            }
        }

        return mapToResponse(config);
    }

    private RealmConfigResponse mapToResponse(RealmConfiguration config) {
        List<ConfiguredService> services = serviceRepository.findByConfigurationId(config.getId());

        List<RealmConfigResponse.ServiceResponse> serviceResponses = services.stream()
                .map(svc -> {
                    List<ConfiguredSubService> subs = subServiceRepository.findByConfiguredServiceId(svc.getId());
                    return RealmConfigResponse.ServiceResponse.builder()
                            .serviceId(svc.getServiceId())
                            .serviceLabel(svc.getServiceLabel())
                            .serviceGroup(svc.getServiceGroup())
                            .subServices(subs.stream().map(ConfiguredSubService::getSubServiceLabel).toList())
                            .build();
                })
                .toList();

        return RealmConfigResponse.builder()
                .id(config.getId())
                .realmId(config.getRealmId())
                .mode(config.getMode())
                .platformVersion(config.getPlatformVersion())
                .deploymentType(config.getDeploymentType())
                .configuredBy(config.getConfiguredBy())
                .configuredAt(config.getConfiguredAt())
                .services(serviceResponses)
                .build();
    }
}
