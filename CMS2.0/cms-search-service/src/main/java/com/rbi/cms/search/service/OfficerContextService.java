package com.rbi.cms.search.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.rbi.cms.search.dto.OfficerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
public class OfficerContextService {

    private final RestClient restClient;
    private final Cache<String, OfficerContext> cache;

    public OfficerContextService(
            @Value("${cms.backend.base-url:http://localhost:8082}") String backendBaseUrl,
            Cache<String, OfficerContext> officerContextCache) {
        this.restClient = RestClient.builder()
                .baseUrl(backendBaseUrl)
                .build();
        this.cache = officerContextCache;
    }

    public OfficerContext getOfficerContext(String userId) {
        if (userId == null || userId.isBlank()) {
            return defaultContext();
        }

        OfficerContext cached = cache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/v1/officers/{userId}", userId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null) {
                return defaultContext();
            }

            OfficerContext context = OfficerContext.builder()
                    .userId(toStr(response.get("userId")))
                    .displayName(toStr(response.get("displayName")))
                    .roleGroup(toStr(response.get("roleGroup")))
                    .regionalOffice(toStr(response.get("regionalOffice")))
                    .build();

            cache.put(userId, context);
            return context;

        } catch (Exception e) {
            log.warn("Failed to fetch officer context for userId={}: {}", userId, e.getMessage());
            return OfficerContext.builder().userId(userId).build();
        }
    }

    private OfficerContext defaultContext() {
        return OfficerContext.builder().build();
    }

    private String toStr(Object value) {
        return value != null ? value.toString() : null;
    }
}
