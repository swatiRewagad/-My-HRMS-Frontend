package com.hrms.cms.service;

import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed reader for SYSTEM_CONFIG, so security thresholds are tunable without a redeploy.
 *
 * A malformed or missing value falls back to the caller's default rather than throwing: a typo in
 * one config row must not take an endpoint offline. The fallback is logged so the bad row is still
 * visible.
 *
 * Values are held in a short-TTL local map instead of the Hazelcast cache used elsewhere. An
 * operator raising a threshold during an incident needs it to take effect promptly, and a
 * cluster-wide cache with no invalidation hook on direct SQL edits would hide the change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final SystemConfigRepository systemConfigRepository;
    private final ConcurrentHashMap<String, CachedValue> cache = new ConcurrentHashMap<>();

    private record CachedValue(String value, long expiresAtNanos) {
        boolean isFresh() {
            return System.nanoTime() < expiresAtNanos;
        }
    }

    public String getString(String key, String fallback) {
        String raw = read(key);
        return (raw == null || raw.isBlank()) ? fallback : raw.trim();
    }

    public int getInt(String key, int fallback) {
        String raw = read(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("SYSTEM_CONFIG {} is not an integer: '{}' — falling back to {}", key, raw, fallback);
            return fallback;
        }
    }

    public long getLong(String key, long fallback) {
        String raw = read(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("SYSTEM_CONFIG {} is not a long: '{}' — falling back to {}", key, raw, fallback);
            return fallback;
        }
    }

    /** Anything other than a recognised true/false literal falls back, so a typo never reads as true. */
    public boolean getBoolean(String key, boolean fallback) {
        String raw = read(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String v = raw.trim();
        if (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes")) {
            return true;
        }
        if (v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("no")) {
            return false;
        }
        log.warn("SYSTEM_CONFIG {} is not a boolean: '{}' — falling back to {}", key, raw, fallback);
        return fallback;
    }

    /** Comma-separated list, order preserved and duplicates dropped. Blank entries are ignored. */
    public Set<String> getSet(String key, Set<String> fallback) {
        String raw = read(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Set<String> values = new LinkedHashSet<>(Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        return values.isEmpty() ? fallback : values;
    }

    private String read(String key) {
        CachedValue cached = cache.get(key);
        if (cached != null && cached.isFresh()) {
            return cached.value();
        }
        String value = systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(null);
        cache.put(key, new CachedValue(value, System.nanoTime() + TTL.toNanos()));
        return value;
    }

    @Transactional
    public SystemConfig upsert(String key, String value, String updatedBy) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseGet(() -> SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        config.setUpdatedBy(updatedBy);
        SystemConfig saved = systemConfigRepository.save(config);
        cache.remove(key);
        return saved;
    }

    public List<SystemConfig> findByPrefix(String prefix) {
        return systemConfigRepository.findAll().stream()
                .filter(c -> c.getConfigKey() != null && c.getConfigKey().startsWith(prefix))
                .sorted((a, b) -> a.getConfigKey().compareTo(b.getConfigKey()))
                .toList();
    }

    public void evict(String key) {
        cache.remove(key);
    }
}
