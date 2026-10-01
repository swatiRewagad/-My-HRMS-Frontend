package com.hrms.cms.service;

import com.hrms.cms.entity.ConfigAuditLog;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.ConfigAuditLogRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final ConfigAuditLogRepository configAuditLogRepository;

    /**
     * Fetch all configs whose key starts with "timeline.".
     */
    public List<SystemConfig> getTimelineConfigs() {
        return systemConfigRepository.findAll().stream()
                .filter(c -> c.getConfigKey() != null && c.getConfigKey().startsWith("timeline."))
                .collect(Collectors.toList());
    }

    /**
     * Update a timeline config value with validation and audit logging.
     *
     * @param key           config key (must start with "timeline.")
     * @param value         new value (must be numeric, 1-365)
     * @param adminUsername admin who made the change
     * @return updated SystemConfig
     */
    @Transactional
    public SystemConfig updateConfig(String key, String value, String adminUsername) {
        // Validate key prefix
        if (key == null || !key.startsWith("timeline.")) {
            throw new IllegalArgumentException("Config key must start with 'timeline.'");
        }

        // Validate numeric range
        int numericValue;
        try {
            numericValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Value must be a valid number");
        }
        if (numericValue < 1 || numericValue > 365) {
            throw new IllegalArgumentException("Value must be between 1 and 365");
        }

        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Config key not found: " + key));

        String oldValue = config.getConfigValue();

        // Update the config
        config.setConfigValue(value);
        config.setUpdatedBy(adminUsername);
        SystemConfig saved = systemConfigRepository.save(config);

        // Audit log
        ConfigAuditLog auditLog = ConfigAuditLog.builder()
                .configKey(key)
                .oldValue(oldValue)
                .newValue(value)
                .changedBy(adminUsername)
                .build();
        configAuditLogRepository.save(auditLog);

        log.info("Timeline config updated: key={}, oldValue={}, newValue={}, by={}",
                key, oldValue, value, adminUsername);

        return saved;
    }

    /**
     * Get a single config value with a fallback default.
     */
    public String getConfigValue(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(getDefaultValue(key));
    }

    /**
     * Get audit log for a config key, ordered by changedAt descending.
     */
    public List<ConfigAuditLog> getAuditLog(String key) {
        return configAuditLogRepository.findByConfigKeyOrderByChangedAtDescIdDesc(key);
    }

    private String getDefaultValue(String key) {
        Map<String, String> defaults = Map.of(
                "timeline.re_response_days", "30",
                "timeline.filing_deadline_days", "365",
                "timeline.crpc_processing_days", "30",
                "timeline.rbio_resolution_days", "30",
                "timeline.appeal_window_days", "30"
        );
        return defaults.getOrDefault(key, "30");
    }
}
