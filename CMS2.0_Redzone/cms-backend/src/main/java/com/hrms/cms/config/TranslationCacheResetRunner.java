package com.hrms.cms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Tomcat starts serving before the CommandLineRunner seeders finish, so a request that arrives
 * mid-seed caches a partially-seeded locale map. @Cacheable never recomputes it, so that locale
 * keeps serving the truncated map until a write evicts it or the TTL expires — and on the
 * simple (dev) cache manager there is no TTL, so it never self-heals.
 *
 * Runs after the highest-ordered seeder so the next read repopulates from the complete table.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class TranslationCacheResetRunner implements CommandLineRunner {

    private final CacheManager cacheManager;

    @Override
    public void run(String... args) {
        for (String name : new String[]{"translations", "translations-module"}) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
        log.info("Cleared translation caches after seeding");
    }
}
