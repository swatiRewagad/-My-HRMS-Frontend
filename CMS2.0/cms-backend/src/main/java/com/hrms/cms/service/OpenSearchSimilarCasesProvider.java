package com.hrms.cms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class OpenSearchSimilarCasesProvider implements SimilarCasesProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchSimilarCasesProvider.class);

    @Value("${cms.similar-cases.opensearch-url:http://localhost:9200}")
    private String opensearchUrl;

    @Value("${cms.similar-cases.index:complaints}")
    private String indexName;

    @Value("${cms.similar-cases.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<Map<String, Object>> findSimilar(String complaintText, String category, int maxResults) {
        if (!enabled) return List.of();

        try {
            String url = opensearchUrl + "/" + indexName + "/_search";

            Map<String, Object> query = buildMoreLikeThisQuery(complaintText, category, maxResults);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            return extractResults(response.getBody());
        } catch (Exception e) {
            log.warn("OpenSearch similar cases lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) return false;
        try {
            restTemplate.getForEntity(opensearchUrl + "/_cluster/health", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "opensearch";
    }

    private Map<String, Object> buildMoreLikeThisQuery(String text, String category, int maxResults) {
        Map<String, Object> mlt = new LinkedHashMap<>();
        mlt.put("fields", List.of("subject", "description", "facts"));
        mlt.put("like", text);
        mlt.put("min_term_freq", 1);
        mlt.put("min_doc_freq", 1);
        mlt.put("max_query_terms", 25);

        Map<String, Object> mltQuery = Map.of("more_like_this", mlt);

        Map<String, Object> boolQuery;
        if (category != null && !category.isBlank()) {
            Map<String, Object> categoryFilter = Map.of("term", Map.of("category", category));
            boolQuery = Map.of("bool", Map.of("must", mltQuery, "filter", categoryFilter));
        } else {
            boolQuery = mltQuery;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", maxResults);
        body.put("query", boolQuery);
        body.put("_source", List.of("complaintNumber", "subject", "status", "category", "createdAt"));

        return body;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractResults(Map<String, Object> responseBody) {
        if (responseBody == null) return List.of();

        Map<String, Object> hits = (Map<String, Object>) responseBody.get("hits");
        if (hits == null) return List.of();

        List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
        if (hitList == null) return List.of();

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> hit : hitList) {
            Map<String, Object> source = (Map<String, Object>) hit.get("_source");
            if (source != null) {
                Map<String, Object> result = new LinkedHashMap<>(source);
                result.put("score", hit.get("_score"));
                results.add(result);
            }
        }
        return results;
    }
}
