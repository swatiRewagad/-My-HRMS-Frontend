package com.hrms.realmaiconfig.service;

import com.hrms.realmaiconfig.dto.*;
import com.hrms.realmaiconfig.entity.RegisteredService;
import com.hrms.realmaiconfig.repository.RegisteredServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceRegistryService {

    private final RegisteredServiceRepository repository;

    public ServiceRegistryStatsResponse getRegistry(String search, String category, String status) {
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;
        String categoryParam = (category != null && !category.isBlank()) ? category.trim() : null;
        String statusParam = (status != null && !status.isBlank()) ? status.trim() : null;

        List<RegisteredService> services = repository.search(searchParam, categoryParam, statusParam);

        return ServiceRegistryStatsResponse.builder()
                .totalServices(repository.count())
                .activeServices(repository.countByStatus("Active"))
                .inactiveServices(repository.countByStatus("Inactive"))
                .deprecatedServices(repository.countByStatus("Deprecated"))
                .services(services.stream().map(this::toResponse).toList())
                .build();
    }

    public RegisteredServiceResponse register(RegisterServiceRequest request) {
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = request.getName().toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
        }

        RegisteredService entity = RegisteredService.builder()
                .name(request.getName())
                .slug(slug)
                .baseUrl(request.getBaseUrl())
                .version(request.getVersion())
                .description(request.getDescription())
                .category(request.getCategory())
                .authType(request.getAuthType())
                .healthCheckEndpoint(request.getHealthCheckEndpoint())
                .ownerName(request.getOwnerName())
                .ownerEmail(request.getOwnerEmail())
                .tags(request.getTags())
                .status(request.getStatus() != null ? request.getStatus() : "Active")
                .build();

        entity = repository.save(entity);
        return toResponse(entity);
    }

    public RegisteredServiceResponse update(Long id, RegisterServiceRequest request) {
        RegisteredService entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found: " + id));

        entity.setName(request.getName());
        entity.setBaseUrl(request.getBaseUrl());
        entity.setVersion(request.getVersion());
        entity.setDescription(request.getDescription());
        entity.setCategory(request.getCategory());
        entity.setAuthType(request.getAuthType());
        entity.setHealthCheckEndpoint(request.getHealthCheckEndpoint());
        entity.setOwnerName(request.getOwnerName());
        entity.setOwnerEmail(request.getOwnerEmail());
        entity.setTags(request.getTags());
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }

        entity = repository.save(entity);
        return toResponse(entity);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private RegisteredServiceResponse toResponse(RegisteredService entity) {
        return RegisteredServiceResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .slug(entity.getSlug())
                .baseUrl(entity.getBaseUrl())
                .version(entity.getVersion())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .authType(entity.getAuthType())
                .healthCheckEndpoint(entity.getHealthCheckEndpoint())
                .ownerName(entity.getOwnerName())
                .ownerEmail(entity.getOwnerEmail())
                .tags(entity.getTags())
                .status(entity.getStatus())
                .registeredAt(entity.getRegisteredAt())
                .build();
    }
}
