package com.hrms.cms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(7)
@Slf4j
public class TranslationCacheWarmup implements CommandLineRunner {

    private final CacheManager cacheManager;

    public TranslationCacheWarmup(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void run(String... args) {
        var translationsCache = cacheManager.getCache("translations");
        if (translationsCache != null) {
            translationsCache.clear();
        }
        var moduleCache = cacheManager.getCache("translations-module");
        if (moduleCache != null) {
            moduleCache.clear();
        }
        log.info("Translation caches cleared after seeding");
    }
}
