package com.rbi.cms.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.opensearch.enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintSearchService {

    private final OpenSearchClient openSearchClient;

    private final ComplaintDocumentNormalizer documentNormalizer;

    public static final String COMPLAINTS_INDEX = "cms-complaints";

    public static final String NODAL_OFFICER_INDEX = "cms-nodal-officer-records";

    /** Configured with timeouts and the backend base URL; see {@code BackendRestClientConfig}. */
    private final RestClient backendRestClient;

    /** One page of a backend stream endpoint, relative to the configured backend base URL. */
    private Map<String, Object> fetchPage(String path, int page, int size) {
        return backendRestClient.get()
                .uri(uriBuilder -> uriBuilder.path(path)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    /**
     * Full-document write. Replaces every field, so callers holding only a subset of the document
     * must use {@link #partialUpdate(String, Map)} instead.
     */
    public void indexComplaint(String complaintId, Map<String, Object> document) throws IOException {
        Map<String, Object> doc = documentNormalizer.normalize(document);
        // A newly indexed complaint is unread. Defaulted here rather than in the normalizer because
        // partialUpdate shares it, and writing a default there would silently un-read every complaint
        // whose status later changes. The dashboard's "Unread Only" filter is a term match on false, so
        // a document missing the field entirely would never appear in it.
        doc.putIfAbsent(ComplaintDocumentNormalizer.FIELD_IS_READ, Boolean.FALSE);
        IndexRequest<Map<String, Object>> request = IndexRequest.of(b -> b
                .index(COMPLAINTS_INDEX)
                .id(complaintId)
                .document(doc));
        openSearchClient.index(request);
        log.info("Indexed complaint: {}", complaintId);
    }

    /**
     * Merges the given fields into an existing document, leaving every other field intact, and
     * creates the document if it is not yet indexed.
     */
    public void partialUpdate(String complaintId, Map<String, Object> partialDocument) throws IOException {
        Map<String, Object> doc = documentNormalizer.normalize(partialDocument);
        UpdateRequest<Map, Map<String, Object>> request =
                new UpdateRequest.Builder<Map, Map<String, Object>>()
                        .index(COMPLAINTS_INDEX)
                        .id(complaintId)
                        .doc(doc)
                        .docAsUpsert(true)
                        .build();
        openSearchClient.update(request, Map.class);
        log.info("Partially updated complaint {} with fields {}", complaintId, doc.keySet());
    }

    public Map<String, Object> reindexAllComplaints() {
        int indexedCount = 0;
        int page = 0;
        int size = 100;
        boolean recordsRemaining = true;

        log.info("Starting fresh bulk reindex operation on index: {}", COMPLAINTS_INDEX);

        try {
            while (recordsRemaining) {
                Map<String, Object> response = fetchPage("/api/complaints/stream", page, size);

                if (response == null || !(response.get("content") instanceof List<?> rawList) || rawList.isEmpty()) {
                    log.info("[REINDEX] Streaming concluded or empty dataset encountered at page {}.", page);
                    break;
                }

                List<Map<String, Object>> complaintsList = (List<Map<String, Object>>) rawList;
                log.info("[REINDEX] Indexing batch from page {}. Records in this page: {}", page, complaintsList.size());

                // 1. Create a container for bulk operations
                List<BulkOperation> operations = new ArrayList<>();

                for (Map<String, Object> complaint : complaintsList) {
                    if (complaint == null) continue;

                    // Shared with the Kafka path so a reindex repairs existing documents in place
                    // instead of writing a second copy of every complaint under a different key.
                    Optional<String> documentId = documentNormalizer.resolveDocumentId(complaint);
                    if (documentId.isEmpty()) {
                        log.warn("[REINDEX] Skipping record with no complaint number: id={}",
                                complaint.get("id"));
                        continue;
                    }

                    try {
                        Map<String, Object> doc = documentNormalizer.normalize(complaint);

                        // 2. Add an individual index operation to the list instead of calling the client directly
                        operations.add(new BulkOperation.Builder()
                                .index(idx -> idx.index(COMPLAINTS_INDEX).id(documentId.get()).document(doc))
                                .build());

                    } catch (Exception ex) {
                        log.error("[REINDEX BATCH ERROR] Skipping broken record [{}]: {}",
                                documentId.get(), ex.getMessage());
                    }
                }

                // 3. Fire the bulk request if we accumulated operations for this page
                if (!operations.isEmpty()) {
                    BulkRequest bulkRequest = new BulkRequest.Builder()
                            .operations(operations)
                            .build();

                    var bulkResponse = openSearchClient.bulk(bulkRequest);

                    // Track actual successfully processed records
                    if (bulkResponse.errors()) {
                        log.error("[REINDEX BULK WARN] Some items failed to index in page {}", page);
                    }
                    indexedCount += operations.size();
                }

                int totalPages = Optional.ofNullable(response.get("totalPages"))
                        .map(obj -> obj instanceof Number num ? num.intValue() : Integer.parseInt(obj.toString()))
                        .orElse(0);

                page++;
                if (page >= totalPages) {
                    recordsRemaining = false;
                }
            }

            openSearchClient.indices().refresh(r -> r.index(COMPLAINTS_INDEX));
            log.info("[REINDEX SUCCESS] Completed execution. Synchronized element count: {}", indexedCount);

        } catch (Exception e) {
            log.error("[REINDEX CRITICAL FAIL] Processing routine crashed on page: " + page, e);
            return Map.of("success", false, "indexed", indexedCount, "error", e.getMessage());
        }

        return Map.of("success", true, "indexed", indexedCount);
    }


    public Map<String, Object> reindexAllNodalOfficers() {
        int indexedCount = 0;
        int page = 0;
        int size = 100;
        boolean recordsRemaining = true;

        try {
            while (recordsRemaining) {
                Map<String, Object> response = fetchPage("/api/v1/re-portal/stream", page, size);

                if (response == null || !response.containsKey("content")) {
                    log.warn("API response is null or missing the 'content' key for NO records at page {}", page);
                    break;
                }

                List<Map<String, Object>> noRecordsList = (List<Map<String, Object>>) response.get("content");
                if (noRecordsList == null || noRecordsList.isEmpty()) {
                    recordsRemaining = false;
                    break;
                }

                // 1. Create a container for bulk operations
                List<BulkOperation> operations = new ArrayList<>();

                for (Map<String, Object> noRecord : noRecordsList) {
                    if (noRecord == null) continue;

                    String id = String.valueOf(noRecord.get("complaintNumber"));
                    Map<String, Object> doc = new HashMap<>(noRecord);

                    String canonical = ComplaintDocumentNormalizer.canonicalStatus(
                            doc.get("status") != null ? doc.get("status").toString() : null);
                    if (canonical != null) {
                        doc.put("status", canonical);
                    }

                    if (doc.get("assignedTo") != null && !doc.containsKey("assignedOfficer")) {
                        doc.put("assignedOfficer", doc.get("assignedTo"));
                    }

                    // 2. Queue the item into bulk operations
                    operations.add(new BulkOperation.Builder()
                            .index(idx -> idx.index(NODAL_OFFICER_INDEX).id(id).document(doc))
                            .build());
                }

                // 3. Execute bulk request for this page
                if (!operations.isEmpty()) {
                    BulkRequest bulkRequest = new BulkRequest.Builder()
                            .operations(operations)
                            .build();

                    openSearchClient.bulk(bulkRequest);
                    indexedCount += operations.size();
                }

                Object totalPagesObj = response.get("totalPages");
                int totalPages = 0;
                if (totalPagesObj instanceof Number) {
                    totalPages = ((Number) totalPagesObj).intValue();
                } else if (totalPagesObj != null) {
                    totalPages = Integer.parseInt(totalPagesObj.toString());
                }

                page++;
                if (page >= totalPages) {
                    recordsRemaining = false;
                }
            }
        } catch (Exception e) {
            log.error("Nodal Officer reindex job broken at page " + page, e);
            return Map.of("success", false, "indexed", indexedCount, "error", e.getMessage());
        }
        log.info("Nodal Officer reindex completed: indexed={}", indexedCount);
        return Map.of("success", true, "indexed", indexedCount);
    }

    private String extractId(Map<String, Object> doc, String... keys) {
        for (String key : keys) {
            Object val = doc.get(key);
            if (val != null) return val.toString();
        }
        return String.valueOf(System.nanoTime());
    }
}
