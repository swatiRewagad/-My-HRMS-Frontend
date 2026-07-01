package com.hrms.cms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarCasesServiceTest {

    @Test
    @DisplayName("should return empty when no provider is available")
    void shouldReturnEmptyWhenNoProvider() {
        SimilarCasesProvider unavailable = new SimilarCasesProvider() {
            @Override public List<Map<String, Object>> findSimilar(String t, String c, int m) { return List.of(); }
            @Override public boolean isAvailable() { return false; }
            @Override public String getProviderName() { return "test"; }
        };

        SimilarCasesService service = new SimilarCasesService(List.of(unavailable));
        List<Map<String, Object>> results = service.findSimilar("test complaint", null, 5);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should use first available provider")
    void shouldUseFirstAvailable() {
        SimilarCasesProvider unavailable = new SimilarCasesProvider() {
            @Override public List<Map<String, Object>> findSimilar(String t, String c, int m) { return List.of(); }
            @Override public boolean isAvailable() { return false; }
            @Override public String getProviderName() { return "offline"; }
        };

        SimilarCasesProvider available = new SimilarCasesProvider() {
            @Override public List<Map<String, Object>> findSimilar(String t, String c, int m) {
                return List.of(Map.of("complaintNumber", "CMS-001", "score", 0.95));
            }
            @Override public boolean isAvailable() { return true; }
            @Override public String getProviderName() { return "mock"; }
        };

        SimilarCasesService service = new SimilarCasesService(List.of(unavailable, available));
        List<Map<String, Object>> results = service.findSimilar("ATM issue", null, 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).containsEntry("complaintNumber", "CMS-001");
    }

    @Test
    @DisplayName("getActiveProvider should return 'none' when no provider available")
    void shouldReturnNoneWhenNoProviderAvailable() {
        SimilarCasesProvider unavailable = new SimilarCasesProvider() {
            @Override public List<Map<String, Object>> findSimilar(String t, String c, int m) { return List.of(); }
            @Override public boolean isAvailable() { return false; }
            @Override public String getProviderName() { return "test"; }
        };

        SimilarCasesService service = new SimilarCasesService(List.of(unavailable));
        assertThat(service.getActiveProvider()).isEqualTo("none");
    }

    @Test
    @DisplayName("getActiveProvider should return name of first available")
    void shouldReturnActiveProviderName() {
        SimilarCasesProvider available = new SimilarCasesProvider() {
            @Override public List<Map<String, Object>> findSimilar(String t, String c, int m) { return List.of(); }
            @Override public boolean isAvailable() { return true; }
            @Override public String getProviderName() { return "opensearch"; }
        };

        SimilarCasesService service = new SimilarCasesService(List.of(available));
        assertThat(service.getActiveProvider()).isEqualTo("opensearch");
    }
}
