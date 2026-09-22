package com.rbi.cms.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.opensearch.enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintSearchService {

    private final OpenSearchClient openSearchClient;

    private static final String INDEX = "cms-complaints";

    public void indexComplaint(String complaintId, Map<String, Object> document) {
        try {
            IndexRequest<Map<String, Object>> request = IndexRequest.of(b -> b
                    .index(INDEX)
                    .id(complaintId)
                    .document(document));
            openSearchClient.index(request);
            log.info("Indexed complaint: {}", complaintId);
        } catch (IOException e) {
            log.error("Failed to index complaint: {}", complaintId, e);
        }
    }

    public List<Map> search(String queryText, String category, String status,
                            String priority, String team, int page, int size) throws IOException {

        List<Query> mustQueries = new ArrayList<>();
        List<Query> filterQueries = new ArrayList<>();

        if (queryText != null && !queryText.isBlank()) {
            mustQueries.add(Query.of(q -> q
                    .multiMatch(mm -> mm
                            .query(queryText)
                            .fields("subject^3", "description^2", "complainantName", "entityName",
                                    "complaintId", "resolutionSummary")
                            .fuzziness("AUTO"))));
        }

        if (category != null && !category.isBlank()) {
            filterQueries.add(Query.of(q -> q.term(t -> t.field("category").value(FieldValue.of(category)))));
        }
        if (status != null && !status.isBlank()) {
            filterQueries.add(Query.of(q -> q.term(t -> t.field("status").value(FieldValue.of(status)))));
        }
        if (priority != null && !priority.isBlank()) {
            filterQueries.add(Query.of(q -> q.term(t -> t.field("priority").value(FieldValue.of(priority)))));
        }
        if (team != null && !team.isBlank()) {
            filterQueries.add(Query.of(q -> q.term(t -> t.field("assignedTeam").value(FieldValue.of(team)))));
        }

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        if (!mustQueries.isEmpty()) boolBuilder.must(mustQueries);
        if (!filterQueries.isEmpty()) boolBuilder.filter(filterQueries);
        if (mustQueries.isEmpty() && filterQueries.isEmpty()) {
            boolBuilder.must(List.of(Query.of(q -> q.matchAll(m -> m))));
        }

        SearchRequest request = SearchRequest.of(b -> b
                .index(INDEX)
                .query(Query.of(q -> q.bool(boolBuilder.build())))
                .from(page * size)
                .size(size)
                .sort(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))));

        SearchResponse<Map> response = openSearchClient.search(request, Map.class);
        return response.hits().hits().stream()
                .map(Hit::source)
                .toList();
    }

    public List<Map> search(String queryText, int page, int size) throws IOException {
        return search(queryText, null, null, null, null, page, size);
    }

    public List<Map> searchByStatus(String status, int page, int size) throws IOException {
        return search(null, null, status, null, null, page, size);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> reindexAll() {
        int indexed = 0;
        int failed = 0;

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "http://localhost:8082/cms-ingestion/api/v1/admin/dashboard/stats";
            Map<String, Object> statsResponse = restTemplate.getForObject(url, Map.class);
            Map<String, Object> data = (Map<String, Object>) statsResponse.get("data");
            List<Map<String, Object>> complaints = (List<Map<String, Object>>) data.get("recentComplaints");

            // Fetch each complaint's full details and index
            for (Map<String, Object> summary : complaints) {
                String complaintId = (String) summary.get("complaintId");
                try {
                    String detailUrl = "http://localhost:8082/cms-ingestion/api/v1/complaints/" + complaintId;
                    Map<String, Object> detailResponse = restTemplate.getForObject(detailUrl, Map.class);
                    Map<String, Object> complaintData = (Map<String, Object>) detailResponse.get("data");
                    indexComplaint(complaintId, complaintData);
                    indexed++;
                } catch (Exception e) {
                    log.warn("Failed to index {}: {}", complaintId, e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            log.error("Reindex failed: {}", e.getMessage());
            return Map.of("error", e.getMessage(), "indexed", indexed, "failed", failed);
        }

        log.info("Reindex completed: indexed={}, failed={}", indexed, failed);
        return Map.of("indexed", indexed, "failed", failed);
    }
}
