package com.rbi.cms.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.rbi.cms.search.config.SearchProperties;
import com.rbi.cms.search.index.ComplaintDocument;
import com.rbi.cms.search.index.LanguageDetector;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.search.enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintSearchService {

    private static final String CB = "elasticsearch";
    private static final String BH = "elasticsearch";

    /**
     * Base (script-correct, unstemmed) fields, always queried so a term in any of the 13 served
     * languages can match something.
     */
    private static final List<String> BASE_FIELDS = List.of(
            "subject^4",
            "description^2",
            "advisoryText",
            "withdrawalReason",
            "schemeCoverageReason",
            "reopenJustification",
            "closureClause.text",
            "complaintNumber^5");

    /**
     * Analyzed subfields for the four languages Elasticsearch 8.15.3 can do more than tokenise for.
     * All four are queried on every request rather than chosen from the request, because a Hindi
     * narrative quoting an English closure clause is the normal case here, not an edge case, and a
     * single-language field list would drop half of such a document.
     */
    private static final List<String> ANALYZED_FIELDS = List.of(
            "subject.en^3", "description.en^2",
            "subject.hi^3", "description.hi^2",
            "subject.bn^3", "description.bn^2",
            "subject.ur^3", "description.ur^2",
            "advisoryText.en", "advisoryText.hi",
            "withdrawalReason.en", "withdrawalReason.hi",
            "schemeCoverageReason.en", "schemeCoverageReason.hi",
            "reopenJustification.en", "reopenJustification.hi");

    private final ElasticsearchClient client;
    private final SearchProperties properties;
    private final LanguageDetector languageDetector;

    /**
     * Upsert, not index.
     *
     * The original method was a full {@code IndexRequest} carrying {@code .document(map)}, and the
     * status-change listener handed it a two-or-three key map. That replaced the whole document with
     * a stub on the first status event, destroying subject and description — the two fields search
     * actually matches on. A partial map must therefore go through {@code _update} with
     * {@code docAsUpsert}, which merges and is also idempotent on redelivery.
     */
    @CircuitBreaker(name = CB)
    @Bulkhead(name = BH)
    public void upsert(String complaintId, Map<String, Object> partialDocument) {
        try {
            UpdateRequest<Map<String, Object>, Map<String, Object>> request = UpdateRequest.of(b -> b
                    .index(properties.getAlias())
                    .id(complaintId)
                    .doc(partialDocument)
                    .docAsUpsert(true));
            client.update(request, Map.class);
            log.debug("Upserted complaint {}", complaintId);
        } catch (IOException e) {
            throw new SearchUnavailableException("Failed to upsert complaint " + complaintId, e);
        }
    }

    @CircuitBreaker(name = CB)
    @Bulkhead(name = BH)
    public void indexFull(String complaintId, ComplaintDocument document) {
        try {
            client.index(b -> b
                    .index(properties.getAlias())
                    .id(complaintId)
                    .document(document));
            log.debug("Indexed complaint {}", complaintId);
        } catch (IOException e) {
            throw new SearchUnavailableException("Failed to index complaint " + complaintId, e);
        }
    }

    /**
     * @throws SearchUnavailableException on any Elasticsearch failure, an open circuit, or a full
     *         bulkhead. Never a fallback to SQL and never a 500.
     */
    @CircuitBreaker(name = CB, fallbackMethod = "searchFallback")
    @Bulkhead(name = BH, fallbackMethod = "searchFallback")
    public SearchPage search(SearchQuery query) {
        try {
            SearchRequest request = buildRequest(query);
            SearchResponse<Map> response = client.search(request, Map.class);

            List<Map<String, Object>> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> row = hit.source() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(hit.source());
                row.put("_score", hit.score());
                results.add(row);
            }

            long total = response.hits().total() == null ? results.size() : response.hits().total().value();
            return new SearchPage(results, total, query.page(), query.size());
        } catch (IOException e) {
            throw new SearchUnavailableException("Elasticsearch query failed", e);
        }
    }

    @SuppressWarnings("unused")
    private SearchPage searchFallback(SearchQuery query, Throwable t) {
        if (t instanceof SearchUnavailableException sue) {
            throw sue;
        }
        log.warn("Search degraded ({}): {}", t.getClass().getSimpleName(), t.getMessage());
        throw new SearchUnavailableException("Search is temporarily unavailable", t);
    }

    private SearchRequest buildRequest(SearchQuery query) {
        List<Query> must = new ArrayList<>();
        List<Query> filter = new ArrayList<>();

        if (query.text() != null && !query.text().isBlank()) {
            List<String> fields = new ArrayList<>(BASE_FIELDS);
            fields.addAll(ANALYZED_FIELDS);

            // best_fields, not cross_fields: the same term is present in several differently-analyzed
            // copies of one field, so summing their scores would reward a stemmer agreeing with
            // itself rather than a better match.
            must.add(Query.of(q -> q.multiMatch(mm -> mm
                    .query(query.text())
                    .fields(fields)
                    .type(TextQueryType.BestFields)
                    .fuzziness("AUTO"))));

            // Timeline remarks are the richest text source in the system but live in a nested doc,
            // so they need their own clause; should() so a complaint matching only on remarks still
            // comes back.
            must.add(Query.of(q -> q.bool(b -> b
                    .should(s -> s.multiMatch(mm -> mm
                            .query(query.text())
                            .fields(List.of("subject", "description"))
                            .type(TextQueryType.BestFields)))
                    .should(s -> s.nested(n -> n
                            .path("timeline")
                            .query(nq -> nq.multiMatch(mm -> mm
                                    .query(query.text())
                                    .fields(List.of("timeline.remarks", "timeline.remarks.en",
                                            "timeline.remarks.hi", "timeline.remarks.bn",
                                            "timeline.remarks.ur"))))
                            .scoreMode(co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode.Max)))
                    .should(s -> s.nested(n -> n
                            .path("appealOrders")
                            .query(nq -> nq.multiMatch(mm -> mm
                                    .query(query.text())
                                    .fields(List.of("appealOrders.orderSummary",
                                            "appealOrders.orderSummary.en", "appealOrders.orderSummary.hi",
                                            "appealOrders.ground", "appealOrders.correctionReason"))))
                            .scoreMode(co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode.Max)))
                    .minimumShouldMatch("1"))));
        }

        addTermFilter(filter, "categoryId", query.categoryId());
        addTermFilter(filter, "status", query.status());
        addTermFilter(filter, "entityCode", query.entityCode());
        addTermFilter(filter, "department", query.department());
        addTermFilter(filter, "workflowStage", query.workflowStage());
        addTermFilter(filter, "milestone", query.milestone());
        addTermFilter(filter, "rbioOfficeCode", query.rbioOfficeCode());
        addTermFilter(filter, "groundOfComplaintId", query.groundOfComplaintId());
        addTermFilter(filter, "compensationType", query.compensationType());
        addTermFilter(filter, "maintainabilityDetermination", query.maintainabilityDetermination());
        addTermFilter(filter, "priority", query.priority());
        addTermFilter(filter, "detectedLanguage", query.language());

        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (!must.isEmpty()) {
            bool.must(must);
        }
        if (!filter.isEmpty()) {
            bool.filter(filter);
        }
        if (must.isEmpty() && filter.isEmpty()) {
            bool.must(Query.of(q -> q.matchAll(m -> m)));
        }

        int size = Math.min(Math.max(query.size(), 1), 100);
        int from = Math.max(query.page(), 0) * size;

        return SearchRequest.of(b -> b
                .index(properties.getAlias())
                .query(Query.of(q -> q.bool(bool.build())))
                .from(from)
                .size(size)
                .trackTotalHits(t -> t.count(10_000))
                .sort(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)
                        .missing(FieldValue.of("_last")))));
    }

    private void addTermFilter(List<Query> filter, String field, String value) {
        if (value != null && !value.isBlank()) {
            filter.add(Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value)))));
        }
    }

    public record SearchQuery(
            String text,
            String categoryId,
            String status,
            String entityCode,
            String department,
            String workflowStage,
            String milestone,
            String rbioOfficeCode,
            String groundOfComplaintId,
            String compensationType,
            String maintainabilityDetermination,
            String priority,
            String language,
            int page,
            int size) {
    }

    public record SearchPage(List<Map<String, Object>> results, long total, int page, int size) {
    }
}
