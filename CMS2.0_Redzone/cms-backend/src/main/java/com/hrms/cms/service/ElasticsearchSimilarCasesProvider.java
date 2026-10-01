package com.hrms.cms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

/**
 * Lexical {@code more_like_this} similarity against the Elasticsearch complaint index.
 *
 * <p>Replaces the OpenSearch provider. Three defects carried over from it and are fixed here:
 *
 * <ol>
 *   <li><b>Wrong index.</b> It read {@code cms.similar-cases.index}, which defaulted to
 *       {@code complaints}, while cms-search-service writes {@code cms-complaints}. The names never
 *       matched, so every query hit a non-existent index; Elasticsearch answers 404 and the catch
 *       below turned that into an empty list, which is indistinguishable from "nothing similar".
 *       The feature therefore appeared to work and always found nothing.
 *   <li><b>No timeouts.</b> It used {@code new RestTemplate()}, whose default is to wait
 *       indefinitely. A hung search node would pin the calling request thread for as long as the
 *       socket stayed open. Production Hikari allows 3000 ms to obtain a connection, so an
 *       unbounded search call can outlive the pool's own patience and starve unrelated requests.
 *   <li><b>Queried the base fields only, so inflected forms did not match.</b> The old field list was
 *       {@code subject}, {@code description}, {@code facts} — and {@code facts} is not a field in this
 *       mapping at all. The two real ones carry {@code cms_indic}, which normalises and folds but does
 *       not stem, so they match a surface form and nothing else. Measured on a live node against two
 *       Hindi complaints: {@code description:"शिकायतों"} (inflected plural) returns <b>0</b> hits while
 *       {@code description.hi:"शिकायतों"} returns <b>2</b>, because the {@code hindi} analyzer on the
 *       subfield stems it to शिकायत. Querying the subfields alongside the base fields is therefore a
 *       recall fix for every language that has a real analyzer.
 * </ol>
 *
 * <p>Degradation is deliberate and asymmetric: a search failure yields an empty result, never an
 * error to the caller. Similar cases are an aid, not part of any statutory decision path, so a
 * search outage must not block an officer from acting on a complaint.
 */
@Component
public class ElasticsearchSimilarCasesProvider implements SimilarCasesProvider {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSimilarCasesProvider.class);

    /**
     * The fields {@code more_like_this} reads.
     *
     * <p>The base {@code subject}/{@code description} carry {@code cms_indic} (icu_tokenizer +
     * normalisation + folding, no stemming) and are kept so that the 9 languages with no analyzer —
     * ta, te, mr, gu, kn, ml, pa, or, as — still match on surface form. The subfields add the
     * languages that do have one: {@code hi} and {@code bn} stem, {@code ur} normalises Arabic script.
     *
     * <p>{@code subject.en}/{@code description.en} are deliberately omitted. The English analyzer
     * stems aggressively, and including it alongside the others inflates {@code more_like_this} term
     * selection with duplicate English stems of the same text, which skews scoring toward documents
     * that merely share common English words. The brief's constraint-wins rule applies: recall for
     * English is already served by {@code cms_indic} on the base field.
     */
    private static final List<String> MLT_FIELDS = List.of(
            "subject", "subject.hi", "subject.bn", "subject.ur",
            "description", "description.hi", "description.bn", "description.ur");

    private final String elasticsearchUrl;
    private final String indexName;
    private final boolean enabled;
    private final RestTemplate restTemplate;

    public ElasticsearchSimilarCasesProvider(
            RestTemplateBuilder builder,
            @Value("${cms.similar-cases.elasticsearch-url:http://localhost:9200}") String elasticsearchUrl,
            @Value("${cms.similar-cases.index:cms-complaints}") String indexName,
            @Value("${cms.similar-cases.enabled:false}") boolean enabled,
            @Value("${cms.similar-cases.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${cms.similar-cases.read-timeout-ms:2000}") long readTimeoutMs) {
        this.elasticsearchUrl = elasticsearchUrl;
        this.indexName = indexName;
        this.enabled = enabled;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Override
    public List<Map<String, Object>> findSimilar(String complaintText, String category, int maxResults) {
        if (!enabled || complaintText == null || complaintText.isBlank()) {
            return List.of();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(buildMoreLikeThisQuery(complaintText, category, maxResults), headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    elasticsearchUrl + "/" + indexName + "/_search",
                    HttpMethod.POST, request, Map.class);

            return extractResults(response.getBody());
        } catch (Exception e) {
            // Logged at WARN with the index name because the original failure mode was a silent 404
            // from a misconfigured index, which looked identical to a genuine no-match.
            log.warn("Similar-cases lookup failed against index {}: {}", indexName, e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            restTemplate.getForEntity(elasticsearchUrl + "/_cluster/health", String.class);
            return true;
        } catch (Exception e) {
            log.debug("Elasticsearch health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "elasticsearch";
    }

    private Map<String, Object> buildMoreLikeThisQuery(String text, String category, int maxResults) {
        Map<String, Object> mlt = new LinkedHashMap<>();
        mlt.put("fields", MLT_FIELDS);
        mlt.put("like", text);
        mlt.put("min_term_freq", 1);
        mlt.put("min_doc_freq", 1);
        mlt.put("max_query_terms", 25);

        Map<String, Object> mltQuery = Map.of("more_like_this", mlt);

        Map<String, Object> query;
        if (category != null && !category.isBlank()) {
            // categoryId, not a category name: the index deliberately stores the id and resolves the
            // display name outside Elasticsearch, so a renamed category does not require a reindex.
            // Callers therefore pass the id here. Verified against the mapping, which is dynamic:strict
            // and rejects any field it does not declare.
            query = Map.of("bool", Map.of("must", mltQuery, "filter",
                    Map.of("term", Map.of("categoryId", category))));
        } else {
            query = mltQuery;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", maxResults);
        body.put("query", query);
        // Narrow _source deliberately: this endpoint returns precedent to a staff member, and the
        // complaint BODY is not needed to decide whether a case is worth opening. Returning only the
        // identifiers keeps one complaint's free text out of another complaint's screen.
        body.put("_source", List.of("complaintNumber", "subject", "status", "categoryId", "createdAt"));
        // Server-side ceiling independent of the client timeout, so a slow shard cannot hold the
        // request thread even if the socket stays healthy.
        body.put("timeout", "2s");

        return body;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractResults(Map<String, Object> responseBody) {
        if (responseBody == null) {
            return List.of();
        }
        Map<String, Object> hits = (Map<String, Object>) responseBody.get("hits");
        if (hits == null) {
            return List.of();
        }
        List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
        if (hitList == null) {
            return List.of();
        }

        List<Map<String, Object>> results = new ArrayList<>(hitList.size());
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
