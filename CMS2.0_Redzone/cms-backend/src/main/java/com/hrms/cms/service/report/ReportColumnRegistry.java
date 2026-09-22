package com.hrms.cms.service.report;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReportColumnDefinition;
import com.hrms.cms.repository.ReportColumnDefinitionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Supplies the report output columns from {@code REPORT_COLUMN_REGISTRY} (UST613).
 *
 * <h2>Why a registry replaced the hardcoded projection</h2>
 * {@code QueryCompiler.executeList} built each row with ten literal {@code row.put(...)} calls, so
 * UST613's 62 columns would have been 52 code edits and a release. Here a column is a row.
 *
 * <h2>Every path is validated against the JPA metamodel before use</h2>
 * {@code JPA_PATH} is interpolated into {@code root.get(path)}. A value that does not name a real
 * {@code Complaint} attribute would throw per-request deep inside query construction, turning a bad
 * config row into a 500 on a report that used to work. Worse, an unvalidated path is a route to
 * projecting a column the report was never meant to expose. So paths are checked against
 * {@code EntityType<Complaint>} once at load and non-resolving rows are DROPPED with a warning —
 * the report degrades by omitting a column rather than by failing entirely.
 *
 * <h2>Caching follows SystemConfigService, not Hazelcast</h2>
 * A short-TTL local reference rather than {@code @Cacheable}. The reasoning is the same one
 * {@code SystemConfigService} documents: an operator adding a column during an incident needs it to
 * appear promptly, and a cluster-wide cache with no invalidation hook on a direct SQL edit would hide
 * the change. It also keeps this off the shared Hazelcast cluster, which in this dev environment is
 * joined by other backend instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportColumnRegistry {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final ReportColumnDefinitionRepository repository;
    private final EntityManager entityManager;

    private final AtomicReference<Cached> cache = new AtomicReference<>();

    private record Cached(List<ReportColumnDefinition> columns, long expiresAtNanos) {
        boolean isFresh() {
            return System.nanoTime() < expiresAtNanos;
        }
    }

    /**
     * The default column set, in {@code DISPLAY_ORDER}. Order is significant: the compiler builds rows
     * into a {@code LinkedHashMap} and the Angular table derives headers from the first row's keys, so
     * this ordering is the on-screen column order.
     *
     * <p>Returns an empty list when the table is empty or unreadable. The compiler treats that as
     * "fall back to the ten built-in columns" rather than emitting no columns at all, because a report
     * with zero columns is indistinguishable from a report with zero rows.
     */
    public List<ReportColumnDefinition> defaultColumns() {
        Cached cached = cache.get();
        if (cached != null && cached.isFresh()) {
            return cached.columns();
        }

        List<ReportColumnDefinition> resolved;
        try {
            resolved = repository.findByIsActiveOrderByDisplayOrderAsc("Y").stream()
                    .filter(ReportColumnDefinition::isDefaultColumn)
                    .filter(this::pathResolves)
                    .toList();
        } catch (Exception e) {
            // A registry read failure must not take reports offline; the compiler's built-in set is
            // still correct, just not extensible.
            log.warn("Could not read REPORT_COLUMN_REGISTRY — falling back to built-in columns: {}",
                    e.getMessage());
            resolved = List.of();
        }

        cache.set(new Cached(resolved, System.nanoTime() + TTL.toNanos()));
        return resolved;
    }

    /** Drops the cached view so a newly seeded or corrected column is picked up immediately. */
    public void evict() {
        cache.set(null);
    }

    /**
     * True when the path names a real singular attribute of {@code Complaint}.
     *
     * <p>Only singular attributes are accepted. A collection or an association would make
     * {@code root.get(path)} produce a join, which silently multiplies result rows — a report that
     * reports each complaint three times because one column traversed a one-to-many is a data-integrity
     * problem, not a cosmetic one.
     */
    private boolean pathResolves(ReportColumnDefinition column) {
        String path = column.getJpaPath();
        if (path == null || path.isBlank()) {
            log.warn("Report column '{}' has no JPA_PATH — dropped", column.getColumnKey());
            return false;
        }
        try {
            EntityType<Complaint> type = entityManager.getMetamodel().entity(Complaint.class);
            type.getSingularAttribute(path);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Report column '{}' names JPA_PATH '{}', which is not a singular attribute of "
                            + "Complaint — dropped so the report degrades by omitting a column rather "
                            + "than failing", column.getColumnKey(), path);
            return false;
        }
    }

    /** The distinct JPA paths the default columns read, for building the Criteria selection. */
    public Set<String> defaultPaths() {
        Set<String> paths = new LinkedHashSet<>();
        for (ReportColumnDefinition column : defaultColumns()) {
            paths.add(column.getJpaPath());
        }
        return paths;
    }

    /** All registered columns including non-default ones, for the admin/diagnostic surface. */
    public List<ReportColumnDefinition> allColumns() {
        try {
            return new ArrayList<>(repository.findByIsActiveOrderByDisplayOrderAsc("Y"));
        } catch (Exception e) {
            log.warn("Could not list REPORT_COLUMN_REGISTRY: {}", e.getMessage());
            return List.of();
        }
    }
}
