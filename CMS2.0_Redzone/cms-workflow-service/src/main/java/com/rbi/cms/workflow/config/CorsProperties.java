package com.rbi.cms.workflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "cms.cors")
public class CorsProperties {

    // Matched as patterns, so entries may contain wildcards (e.g. http://localhost:*).
    private List<String> allowedOrigins = new ArrayList<>();

    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

    private List<String> exposedHeaders = new ArrayList<>();

    private boolean allowCredentials = true;

    private long maxAge = 3600L;
}
