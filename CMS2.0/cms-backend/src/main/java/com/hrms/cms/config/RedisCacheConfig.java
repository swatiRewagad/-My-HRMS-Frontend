package com.hrms.cms.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Profile("!dev")
public class RedisCacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("dashboard", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigs.put("categories", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("categories-root", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("categories-sub", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("banks", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("banks-by-type", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("form-config", defaultConfig.entryTtl(Duration.ofHours(6)));
        cacheConfigs.put("email-stats", defaultConfig.entryTtl(Duration.ofMinutes(3)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
