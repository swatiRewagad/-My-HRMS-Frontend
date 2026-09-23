package com.rbi.cms.search.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rbi.cms.search.dto.OfficerContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, OfficerContext> officerContextCache(
            @Value("${cms.officer-cache.ttl-minutes:5}") long ttlMinutes,
            @Value("${cms.officer-cache.max-size:500}") long maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .build();
    }
}
