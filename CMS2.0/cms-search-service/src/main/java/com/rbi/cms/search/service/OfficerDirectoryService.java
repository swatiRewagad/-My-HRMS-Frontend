package com.rbi.cms.search.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves an officer username to a display name, for enrichment at index time.
 *
 * <p>Display names live only in Keycloak — there is no such column in any database — so this is the
 * only source. It holds the <em>whole</em> directory as one cache entry rather than one entry per
 * officer: the map is bounded by staff count, and a single bulk fetch avoids a per-complaint fan-out
 * during a reindex that walks the entire corpus.
 *
 * <p>Unlike an authorization attribute, a stale entry here is harmless — it renders a former label, it
 * cannot widen what anyone can see — which is why caching is acceptable for this and not for the
 * officer's department. Serving a stale map on failure is likewise deliberate: a Keycloak outage should
 * degrade the rendered name, never fail an indexing run.
 *
 * <p>Must not be called on the search request path. Names are baked into the document by
 * {@link ComplaintDocumentNormalizer} at write time, so reads never depend on Keycloak being up.
 */
@Slf4j
@Service
public class OfficerDirectoryService {

    private static final String DIRECTORY_PATH = "/api/v1/keycloak/users/directory";
    private static final String CACHE_KEY = "directory";

    /** {@code buildDisplayName} upstream already substitutes this when a user has no name set. */
    private static final String UPSTREAM_UNRESOLVED = "Unknown";

    private final RestClient backendRestClient;
    private final LoadingCache<String, Map<String, String>> cache;

    public OfficerDirectoryService(RestClient backendRestClient,
                                   @Value("${cms.officer-directory.refresh-interval-minutes:10}") long refreshMinutes) {
        this.backendRestClient = backendRestClient;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1)
                // refreshAfterWrite, not expireAfterWrite: it serves the previous map while reloading in
                // the background, so no indexing thread ever blocks on a Keycloak round trip.
                .refreshAfterWrite(Duration.ofMinutes(refreshMinutes))
                .build(key -> fetchDirectory());
    }

    /**
     * The display name for a username, or empty when it cannot be resolved.
     *
     * <p>Callers are expected to fall back to the username itself. A username is actionable — an officer
     * can be looked up by it — whereas a blank cell or the word "Unknown" is not.
     */
    public Optional<String> displayNameFor(String userName) {
        if (userName == null || userName.isBlank()) {
            return Optional.empty();
        }
        String resolved = directory().get(userName.strip());
        if (resolved == null || resolved.isBlank() || UPSTREAM_UNRESOLVED.equalsIgnoreCase(resolved)) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    private Map<String, String> directory() {
        Map<String, String> directory = cache.get(CACHE_KEY);
        return directory != null ? directory : Map.of();
    }

    private Map<String, String> fetchDirectory() {
        try {
            Map<String, String> directory = backendRestClient.get()
                    .uri(DIRECTORY_PATH)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, String>>() { });

            if (directory == null || directory.isEmpty()) {
                log.warn("Officer directory at {} returned no entries; assigned-officer names will fall "
                        + "back to usernames", DIRECTORY_PATH);
                return Map.of();
            }
            log.info("Loaded officer directory with {} entries", directory.size());
            return Map.copyOf(directory);
        } catch (Exception e) {
            // Returning the previous value keeps a refresh failure from clearing the cache. On the very
            // first load there is nothing to keep, so indexing proceeds with usernames.
            Map<String, String> stale = cache.asMap().get(CACHE_KEY);
            log.error("Failed to load officer directory from {}; {}", DIRECTORY_PATH,
                    stale == null ? "falling back to usernames" : "serving the previously loaded map", e);
            return stale != null ? stale : Map.of();
        }
    }
}
