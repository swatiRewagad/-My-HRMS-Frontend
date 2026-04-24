package com.hrms.realmaiconfig.controller;

import com.hrms.realmaiconfig.dto.*;
import com.hrms.realmaiconfig.service.ServiceRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/service-registry")
@RequiredArgsConstructor
public class ServiceRegistryController {

    private final ServiceRegistryService serviceRegistryService;

    @GetMapping
    public ResponseEntity<ServiceRegistryStatsResponse> getRegistry(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(serviceRegistryService.getRegistry(search, category, status));
    }

    @PostMapping
    public ResponseEntity<RegisteredServiceResponse> register(
            @Valid @RequestBody RegisterServiceRequest request) {
        return ResponseEntity.ok(serviceRegistryService.register(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RegisteredServiceResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RegisterServiceRequest request) {
        return ResponseEntity.ok(serviceRegistryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        serviceRegistryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
