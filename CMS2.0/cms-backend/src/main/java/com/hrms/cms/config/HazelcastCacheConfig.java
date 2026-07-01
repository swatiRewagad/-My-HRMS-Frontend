package com.hrms.cms.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.cache.HazelcastCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HazelcastCacheConfig {

    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setInstanceName("cms-hazelcast");
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

        config.addMapConfig(new MapConfig("dashboard")
                .setTimeToLiveSeconds(120));

        config.addMapConfig(new MapConfig("categories")
                .setTimeToLiveSeconds(3600));

        config.addMapConfig(new MapConfig("categories-root")
                .setTimeToLiveSeconds(3600));

        config.addMapConfig(new MapConfig("categories-sub")
                .setTimeToLiveSeconds(3600));

        config.addMapConfig(new MapConfig("banks")
                .setTimeToLiveSeconds(3600));

        config.addMapConfig(new MapConfig("banks-by-type")
                .setTimeToLiveSeconds(3600));

        config.addMapConfig(new MapConfig("form-config")
                .setTimeToLiveSeconds(21600));

        config.addMapConfig(new MapConfig("email-stats")
                .setTimeToLiveSeconds(180));

        config.addMapConfig(new MapConfig("holidays")
                .setTimeToLiveSeconds(86400));

        config.addMapConfig(new MapConfig("translations")
                .setTimeToLiveSeconds(1800));

        config.addMapConfig(new MapConfig("translations-module")
                .setTimeToLiveSeconds(1800));

        config.addMapConfig(new MapConfig("default")
                .setTimeToLiveSeconds(300));

        return config;
    }

    @Bean
    public HazelcastInstance hazelcastInstance(Config hazelcastConfig) {
        return Hazelcast.newHazelcastInstance(hazelcastConfig);
    }

    @Bean
    public CacheManager cacheManager(HazelcastInstance hazelcastInstance) {
        return new HazelcastCacheManager(hazelcastInstance);
    }
}
