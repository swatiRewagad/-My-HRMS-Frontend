package com.hrms.cms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SimilarCasesService {

    private static final Logger log = LoggerFactory.getLogger(SimilarCasesService.class);

    private final List<SimilarCasesProvider> providers;

    public SimilarCasesService(List<SimilarCasesProvider> providers) {
        this.providers = providers;
    }

    public List<Map<String, Object>> findSimilar(String complaintText, String category, int maxResults) {
        for (SimilarCasesProvider provider : providers) {
            if (provider.isAvailable()) {
                log.debug("Using provider: {}", provider.getProviderName());
                return provider.findSimilar(complaintText, category, maxResults);
            }
        }
        log.info("No similar-cases provider available");
        return List.of();
    }

    public String getActiveProvider() {
        return providers.stream()
            .filter(SimilarCasesProvider::isAvailable)
            .findFirst()
            .map(SimilarCasesProvider::getProviderName)
            .orElse("none");
    }
}
