package com.hrms.realmaiconfig.controller;

import com.hrms.realmaiconfig.dto.RealmConfigRequest;
import com.hrms.realmaiconfig.dto.RealmConfigResponse;
import com.hrms.realmaiconfig.service.RealmConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/realm-config")
@RequiredArgsConstructor
public class RealmConfigController {

    private final RealmConfigService realmConfigService;

    @GetMapping("/{realmId}")
    public ResponseEntity<RealmConfigResponse> getConfiguration(@PathVariable String realmId) {
        return realmConfigService.getConfigurationByRealmId(realmId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RealmConfigResponse> saveConfiguration(
            @Valid @RequestBody RealmConfigRequest request) {
        RealmConfigResponse response = realmConfigService.saveConfiguration(request);
        return ResponseEntity.ok(response);
    }
}
