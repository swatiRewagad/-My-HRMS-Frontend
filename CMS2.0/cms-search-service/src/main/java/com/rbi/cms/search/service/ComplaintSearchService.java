package com.rbi.cms.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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

    public static final String COMPLAINTS_INDEX = "cms-complaints";
    public static final String NODAL_OFFICER_INDEX = "cms-nodal-officer-records";

    @Value("${cms.backend.base-url:http://localhost:8082}")
    private String backendBaseUrl;

    public void indexComplaint(String complaintId, Map<String, Object> document) {
        try {
            IndexRequest<Map<String, Object>> request = IndexRequest.of(b -> b
                    .index(COMPLAINTS_INDEX)
                    .id(complaintId)
                    .document(document));
            openSearchClient.index(request);
            log.info("Indexed complaint: {}", complaintId);
        } catch (IOException e) {
            log.error("Failed to index complaint: {}", complaintId, e);
        }
    }

    public void indexNodalOfficerRecord(String id, Map<String, Object> document) {
        try {
            IndexRequest<Map<String, Object>> request = IndexRequest.of(b -> b
                    .index(NODAL_OFFICER_INDEX)
                    .id(id)
                    .document(document));
            openSearchClient.index(request);
            log.debug("Indexed nodal officer record: {}", id);
        } catch (IOException e) {
            log.error("Failed to index nodal officer record: {}", id, e);
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
                .index(COMPLAINTS_INDEX)
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
        int complaintsIndexed = 0;
        int complaintsFailed = 0;
        int nodalIndexed = 0;
        int nodalFailed = 0;

        RestTemplate restTemplate = new RestTemplate();

        // 1. Fetch and index all complaints from cms-backend
        try {
            String complaintsUrl = backendBaseUrl + "/api/complaints";
            log.info("Fetching all complaints from: {}", complaintsUrl);

            ResponseEntity<List<Map<String, Object>>> complaintsResponse = restTemplate.exchange(
                    complaintsUrl, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> complaints = complaintsResponse.getBody();
            if (complaints != null && !complaints.isEmpty()) {
                log.info("Fetched {} complaints, starting bulk indexing...", complaints.size());

                List<Map<String, Object>> batch = new ArrayList<>();
                for (Map<String, Object> complaint : complaints) {
                    batch.add(complaint);
                    if (batch.size() >= 100) {
                        int[] result = bulkIndexComplaints(batch);
                        complaintsIndexed += result[0];
                        complaintsFailed += result[1];
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    int[] result = bulkIndexComplaints(batch);
                    complaintsIndexed += result[0];
                    complaintsFailed += result[1];
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch/index complaints: {}", e.getMessage(), e);
        }

        // 2. Fetch and index all nodal officer records from cms-backend
        try {
            String nodalUrl = backendBaseUrl + "/api/nodal-officer-records";
            log.info("Fetching all nodal officer records from: {}", nodalUrl);

            ResponseEntity<List<Map<String, Object>>> nodalResponse = restTemplate.exchange(
                    nodalUrl, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> nodalRecords = nodalResponse.getBody();
            if (nodalRecords != null && !nodalRecords.isEmpty()) {
                log.info("Fetched {} nodal officer records, starting bulk indexing...", nodalRecords.size());

                List<Map<String, Object>> batch = new ArrayList<>();
                for (Map<String, Object> record : nodalRecords) {
                    batch.add(record);
                    if (batch.size() >= 100) {
                        int[] result = bulkIndexNodalRecords(batch);
                        nodalIndexed += result[0];
                        nodalFailed += result[1];
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    int[] result = bulkIndexNodalRecords(batch);
                    nodalIndexed += result[0];
                    nodalFailed += result[1];
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch/index nodal officer records: {}", e.getMessage(), e);
        }

        log.info("Reindex completed: complaints[indexed={}, failed={}], nodal[indexed={}, failed={}]",
                complaintsIndexed, complaintsFailed, nodalIndexed, nodalFailed);

        return Map.of(
                "complaintsIndexed", complaintsIndexed,
                "complaintsFailed", complaintsFailed,
                "nodalOfficerRecordsIndexed", nodalIndexed,
                "nodalOfficerRecordsFailed", nodalFailed
        );
    }

    private int[] bulkIndexComplaints(List<Map<String, Object>> complaints) {
        int indexed = 0;
        int failed = 0;

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (Map<String, Object> complaint : complaints) {
                String id = extractId(complaint, "id", "complaintId");
                String complaintNumber = complaint.get("complaintNumber") != null
                        ? complaint.get("complaintNumber").toString() : id;

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(COMPLAINTS_INDEX)
                                .id(complaintNumber)
                                .document(complaint)));
            }

            BulkResponse response = openSearchClient.bulk(bulkBuilder.build());
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    log.warn("Failed to index complaint {}: {}", item.id(), item.error().reason());
                    failed++;
                } else {
                    indexed++;
                }
            }
        } catch (IOException e) {
            log.error("Bulk index complaints failed: {}", e.getMessage(), e);
            failed += complaints.size();
        }

        return new int[]{indexed, failed};
    }

    private int[] bulkIndexNodalRecords(List<Map<String, Object>> records) {
        int indexed = 0;
        int failed = 0;

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (Map<String, Object> record : records) {
                String id = extractId(record, "id", "complaintNumber");

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(NODAL_OFFICER_INDEX)
                                .id(id)
                                .document(record)));
            }

            BulkResponse response = openSearchClient.bulk(bulkBuilder.build());
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    log.warn("Failed to index nodal record {}: {}", item.id(), item.error().reason());
                    failed++;
                } else {
                    indexed++;
                }
            }
        } catch (IOException e) {
            log.error("Bulk index nodal records failed: {}", e.getMessage(), e);
            failed += records.size();
        }

        return new int[]{indexed, failed};
    }

    private String extractId(Map<String, Object> doc, String... keys) {
        for (String key : keys) {
            Object val = doc.get(key);
            if (val != null) return val.toString();
        }
        return String.valueOf(System.nanoTime());
    }
}
