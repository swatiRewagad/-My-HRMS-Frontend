package com.rbi.cms.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import com.rbi.cms.search.config.SearchProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the lifecycle of the complaint index: settings, mapping, and the alias readers go through.
 *
 * <p>The index is never created implicitly by a first write. Dynamic mapping is what produced the
 * original defect where {@code category} and {@code status} came out as {@code text} and so could
 * not be {@code term}-filtered, and {@code createdAt} came out as {@code text} and so could not be
 * sorted. Both the settings and the mapping are reviewable JSON on the classpath, not Java builders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.search.enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintIndexManager {

    private static final String SETTINGS_JSON = "elasticsearch/cms-complaints-settings.json";
    private static final String MAPPINGS_JSON = "elasticsearch/cms-complaints-mappings.json";

    private static final DateTimeFormatter SUFFIX =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ElasticsearchClient client;
    private final SearchProperties properties;

    /**
     * Startup is best-effort on purpose. A search service that refuses to boot because Elasticsearch
     * is briefly unreachable turns a degraded dependency into an outage of this pod, and the
     * degradation rules already say search must fail soft.
     */
    @PostConstruct
    public void ensureAliasExists() {
        try {
            if (aliasExists()) {
                log.info("Alias '{}' already present", properties.getAlias());
                return;
            }
            String index = createVersionedIndex();
            pointAliasAt(index);
            log.info("Created index '{}' and pointed alias '{}' at it", index, properties.getAlias());
        } catch (Exception e) {
            log.warn("Could not ensure index alias '{}' at startup: {}. Search will report unavailable"
                    + " until Elasticsearch is reachable.", properties.getAlias(), e.getMessage());
        }
    }

    public boolean aliasExists() throws IOException {
        return client.indices().existsAlias(a -> a.name(properties.getAlias())).value();
    }

    /**
     * Creates a new concrete index carrying the reviewed settings and mapping, named with a UTC
     * timestamp suffix. The alias is not touched — see {@link #swapAliasTo(String)}.
     */
    public String createVersionedIndex() throws IOException {
        String indexName = properties.getAlias() + "-" + SUFFIX.format(Instant.now());

        try (InputStream settings = new ClassPathResource(SETTINGS_JSON).getInputStream();
             InputStream mappings = new ClassPathResource(MAPPINGS_JSON).getInputStream()) {

            // cms-complaints-settings.json is shaped like the body of _settings ({"index":..,
            // "analysis":..}), so it must be deserialized INTO the settings slot. Passing it to
            // CreateIndexRequest.withJson instead treats "index" as a request field and throws
            // "Unknown field 'index'" on every call.
            CreateIndexRequest request = CreateIndexRequest.of(b -> b
                    .index(indexName)
                    .settings(s -> s.withJson(settings))
                    .mappings(m -> m.withJson(mappings)));

            client.indices().create(request);
        }

        log.info("Created index {}", indexName);
        return indexName;
    }

    private void pointAliasAt(String index) throws IOException {
        client.indices().updateAliases(u -> u.actions(
                Action.of(a -> a.add(add -> add.index(index).alias(properties.getAlias())))));
    }

    /**
     * Repoints the alias at {@code newIndex} and removes it from whatever it pointed at, in one
     * atomic _aliases call.
     *
     * Atomicity is the whole point: a reindex that removed the old alias and then added the new one
     * would leave a window where the alias resolves to nothing and every search 404s, and a reindex
     * that populated the live index in place would serve partially-populated results for its whole
     * duration.
     *
     * @return the indices the alias was removed from, so the caller can decide when to delete them
     */
    public List<String> swapAliasTo(String newIndex) throws IOException {
        List<String> previous = currentIndices();

        List<Action> actions = new ArrayList<>();
        actions.add(Action.of(a -> a.add(add -> add.index(newIndex).alias(properties.getAlias()))));
        for (String old : previous) {
            if (!old.equals(newIndex)) {
                actions.add(Action.of(a -> a.remove(rm -> rm.index(old).alias(properties.getAlias()))));
            }
        }

        client.indices().updateAliases(u -> u.actions(actions));
        log.info("Alias '{}' now -> {} (detached from {})", properties.getAlias(), newIndex, previous);
        return previous;
    }

    public List<String> currentIndices() throws IOException {
        if (!aliasExists()) {
            return List.of();
        }
        GetAliasResponse response = client.indices().getAlias(a -> a.name(properties.getAlias()));
        return new ArrayList<>(response.result().keySet());
    }

    public void deleteIndex(String index) throws IOException {
        client.indices().delete(d -> d.index(index));
        log.info("Deleted index {}", index);
    }

    /** Flips an index back to normal refresh behaviour after a bulk load. */
    public void restoreRefreshInterval(String index) throws IOException {
        client.indices().putSettings(s -> s.index(index).settings(t -> t.refreshInterval(r -> r.time("5s"))));
        client.indices().refresh(r -> r.index(index));
    }

    /** Disables refresh for the duration of a bulk load. */
    public void suspendRefresh(String index) throws IOException {
        client.indices().putSettings(s -> s.index(index).settings(t -> t.refreshInterval(r -> r.time("-1"))));
    }
}
