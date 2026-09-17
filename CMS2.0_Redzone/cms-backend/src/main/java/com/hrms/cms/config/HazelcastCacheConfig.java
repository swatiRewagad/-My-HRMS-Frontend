package com.hrms.cms.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.cache.HazelcastCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HazelcastCacheConfig {

    @Value("${cms.hazelcast.cluster-name:cms-cluster}")
    private String clusterName;

    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setInstanceName("cms-hazelcast");

        // Multicast and TCP/IP joins are both off below, so this node is meant to stand alone — but
        // Hazelcast's auto-detection join is enabled by default and silently forms a cluster anyway.
        // A second instance on the same host then shares these caches, so a value written by one node
        // is served to the other: a deliberately corrupted DB row read back clean through the API
        // because the other node's cached bundle answered. Disabling auto-detection and namespacing
        // the cluster keeps a locally-run instance genuinely isolated.
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.setClusterName(clusterName);

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

        config.addMapConfig(new MapConfig("mre-rules")
                .setTimeToLiveSeconds(3600));

        config.addMapConfig(new MapConfig("copilot-precedent")
                .setTimeToLiveSeconds(600));

        config.addMapConfig(new MapConfig("report-results")
                .setTimeToLiveSeconds(300));

        config.addMapConfig(new MapConfig("analytics-summary")
                .setTimeToLiveSeconds(300));

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
