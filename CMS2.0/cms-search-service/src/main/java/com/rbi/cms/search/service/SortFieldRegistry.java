package com.rbi.cms.search.service;

import com.rbi.cms.common.exception.CmsException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Whitelist translating the grid's sort keys to indexed field names.
 *
 * <p>Extracted from the query service so it can be asserted on directly. One of the entries had
 * silently accumulated a combining diacritic (U+0325) inside {@code "filingType.keyword"}, which
 * compiles and reads correctly but names a field that does not exist — sorting by it did nothing.
 * {@code SortFieldRegistryTest} guards the whole map against that class of defect.
 */
public final class SortFieldRegistry {

    /**
     * Sorted by a painless script over {@code slaDeadline} rather than a stored field, so it is not a
     * name this map can translate.
     */
    public static final String SLA_BREACH_IN = "slaBreachIn";

    public static final String DEFAULT_SORT_FIELD = "createdAt";

    private static final Map<String, String> FRONTEND_TO_OPENSEARCH_FIELD = Map.ofEntries(
            // Both identifiers resolve to the business complaint number: the numeric `id` is only
            // present on documents written by the reindex path, so sorting on it would order just
            // the part of the corpus that has never been touched by a Kafka event.
            Map.entry("complaintId", "complaintNumber.keyword"),
            Map.entry("complaintNumber", "complaintNumber.keyword"),
            // Still the username, deliberately. The grid now displays assignedOfficerName, so this
            // sorts by a different value than it shows — but only documents written since that field
            // was introduced carry it, and switching early would sort the rest as missing. Flip to
            // "assignedOfficerName.keyword" once a full backfill reindex has populated the corpus.
            Map.entry("assignedTo", "assignedOfficer.keyword"),
            Map.entry("mode", "filingType.keyword"),
            Map.entry("complainantName", "complainantName.keyword"),
            Map.entry("status", "status.keyword"),
            Map.entry("entityName", "entityName.keyword"),
            Map.entry("complaintCategory", "categoryName.keyword"),
            Map.entry("createdDate", "createdAt"),
            Map.entry("lastUpdatedDate", "updatedAt"),
            Map.entry("priority", "priority.keyword"),
            Map.entry("subject", "subject.keyword")
    );

    private SortFieldRegistry() {
    }

    public static boolean isScriptSort(String frontendProperty) {
        return SLA_BREACH_IN.equals(frontendProperty);
    }

    public static Optional<String> resolve(String frontendProperty) {
        return Optional.ofNullable(FRONTEND_TO_OPENSEARCH_FIELD.get(frontendProperty));
    }

    /**
     * Rejects an unknown sort key instead of dropping it. A dropped key returns a correctly paginated
     * page in the wrong order, which the caller cannot detect — so silently ignoring it hands back
     * plausible but wrong data.
     */
    public static String require(String frontendProperty) {
        return resolve(frontendProperty).orElseThrow(() -> new CmsException(
                "Unsupported sort field: '" + frontendProperty + "'. Allowed: " + String.join(", ", allowed()),
                HttpStatus.BAD_REQUEST));
    }

    /** Sorted so error messages and assertions are stable. */
    public static Set<String> allowed() {
        Set<String> keys = new TreeSet<>(FRONTEND_TO_OPENSEARCH_FIELD.keySet());
        keys.add(SLA_BREACH_IN);
        return keys;
    }

    static Map<String, String> mappings() {
        return FRONTEND_TO_OPENSEARCH_FIELD;
    }
}
