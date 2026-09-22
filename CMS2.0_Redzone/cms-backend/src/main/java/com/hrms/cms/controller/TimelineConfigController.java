package com.hrms.cms.controller;

import com.hrms.cms.entity.ConfigAuditLog;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.service.TimelineConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/config/timelines")
@RequiredArgsConstructor
public class TimelineConfigController {

    private final TimelineConfigService timelineConfigService;

    /**
     * GET all timeline-related configs from SYSTEM_CONFIG where key starts with "timeline."
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getTimelineConfigs() {
        List<SystemConfig> configs = timelineConfigService.getTimelineConfigs();
        List<Map<String, Object>> result = configs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * PUT — update a single timeline config value.
     * Validates: value must be numeric, between 1 and 365.
     * Logs the change in CONFIG_AUDIT_LOG.
     */
    @PutMapping("/{configKey}")
    public ResponseEntity<?> updateTimelineConfig(
            @PathVariable String configKey,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {

        String value = body.get("value");
        if (value == null || value.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Value is required"));
        }

        String adminUsername = (userName != null && !userName.isBlank()) ? userName : "admin";

        try {
            SystemConfig updated = timelineConfigService.updateConfig(configKey, value, adminUsername);
            return ResponseEntity.ok(toDto(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET audit log for a specific config key.
     */
    @GetMapping("/audit/{configKey}")
    public ResponseEntity<List<Map<String, Object>>> getAuditLog(@PathVariable String configKey) {
        List<ConfigAuditLog> logs = timelineConfigService.getAuditLog(configKey);
        List<Map<String, Object>> result = logs.stream()
                .map(this::toAuditDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toDto(SystemConfig config) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", config.getId());
        dto.put("configKey", config.getConfigKey());
        dto.put("configValue", config.getConfigValue());
        dto.put("description", config.getDescription());
        dto.put("updatedBy", config.getUpdatedBy());
        dto.put("updatedAt", config.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toAuditDto(ConfigAuditLog log) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", log.getId());
        dto.put("configKey", log.getConfigKey());
        dto.put("oldValue", log.getOldValue());
        dto.put("newValue", log.getNewValue());
        dto.put("changedBy", log.getChangedBy());
        dto.put("changedAt", log.getChangedAt());
        return dto;
    }
}
