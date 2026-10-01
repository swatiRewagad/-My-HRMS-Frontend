package com.hrms.cms.service.dashboard;

import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.projection.DashboardProjections;
import com.hrms.cms.service.TatCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only aggregates for the senior-management dashboard.
 *
 * <p>Every panel here used to begin with {@code complaintRepo.findAll()} — five of them, one per
 * method, and six calls in total once {@code /summary} fanned out. {@code COMPLAINTS} has 105 columns
 * including six {@code TEXT} bodies, so each call hydrated the whole table as managed entities in
 * order to compute a few dozen integers. The panels now issue grouped or narrowed queries and this
 * class only assembles the response.
 *
 * <p><b>Case folding is the one correctness hazard in that translation.</b> The table collates
 * {@code utf8mb4_0900_ai_ci}, so SQL folds case where the {@code Collectors.groupingBy} it replaces
 * did not. Grouping moved into SQL ONLY for columns measured to have no case collisions; see
 * {@link #getBottlenecks()} for the {@code priority} column, which does collide and is therefore
 * still tallied in Java.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeniorDashboardService {

    /** Statuses treated as terminal by TAT and entity-breach reporting. */
    private static final List<String> CLOSED_STATUSES = List.of("closed", "withdrawn");

    /** Statuses that constitute "backlog" for the bottleneck panel. */
    private static final List<String> BACKLOG_STATUSES = List.of("pending", "in_progress");

    /** Statuses that count as dealt-with for the per-entity resolved tally. */
    private static final List<String> RESOLVED_STATUSES = List.of("resolved", "closed");

    private static final int TREND_WEEKS = 12;

    /**
     * Hard ceiling on rows pulled for a TAT pass.
     *
     * <p>TAT cannot be pushed into SQL (see {@link #getTatAnalytics()}), so this is the one panel that
     * still reads per-row data and the only one that needs a cap at all. 100k narrow 3-field rows is
     * a few megabytes — two orders of magnitude above the current 741 active complaints, so it does
     * not bite today, but it converts "the dashboard OOMs the pod" into "the dashboard reports a
     * truncated number" if the active backlog ever runs away.
     */
    private static final int TAT_ROW_CAP = 100_000;

    private final ComplaintRepository complaintRepo;
    private final ComplaintCategoryRepository categoryRepo;
    private final TatCalculationService tatService;

    /**
     * Lazily-built id-to-name map for complaint categories.
     *
     * <p>Assigned only as a fully-built map and never mutated in place. The previous version created
     * an empty {@code HashMap}, published it to this field and then filled it, so a concurrent request
     * on this singleton could read a partially-populated map and label a category
     * {@code "Category-7"} for one caller and correctly for the next. Two dashboard panels are served
     * concurrently by {@code /summary}, so that was reachable.
     */
    private volatile Map<Long, String> categoryNameCache = null;

    private Map<Long, String> categoryNames() {
        Map<Long, String> cached = categoryNameCache;
        if (cached != null) {
            return cached;
        }
        Map<Long, String> built = new HashMap<>();
        try {
            categoryRepo.findAll().forEach(c -> built.put(c.getId(), c.getName()));
        } catch (Exception e) {
            log.debug("Could not load category names: {}", e.getMessage());
        }
        categoryNameCache = built;
        return built;
    }

    private String getCategoryName(Long categoryId) {
        if (categoryId == null) return "Uncategorized";
        return categoryNames().getOrDefault(categoryId, "Category-" + categoryId);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    // PIPELINE
    // ═════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Headline counts per pipeline stage.
     *
     * <p>One {@code GROUP BY status} query (22 groups, covering index-only scan) replaces a
     * whole-table hydrate plus six sequential {@code stream().filter().count()} passes.
     *
     * <p>{@code status} was measured to have no case collisions — 22 distinct values under both
     * {@code CAST(... AS BINARY)} and the case-insensitive default — so the group keys come back in
     * their stored spelling and the case-SENSITIVE lookups below reproduce the previous
     * {@code "pending".equals(...)} comparisons exactly. {@code total} is the sum of all groups rather
     * than a separate {@code COUNT(*)}: {@code status} is {@code NOT NULL} (0 nulls measured), so no
     * row can fall outside a group, and summing avoids a second query that could disagree with the
     * first under concurrent writes.
     */
    @Cacheable(value = "analytics-summary", key = "'senior-pipeline'")
    public Map<String, Object> getPipelineSummary() {
        Map<String, Long> byStatus = new HashMap<>();
        long total = 0;
        for (DashboardProjections.KeyCount row : complaintRepo.countGroupedByStatus()) {
            if (row.groupKey() != null) {
                byStatus.put(row.groupKey(), row.total());
            }
            total += row.total();
        }

        long pending = byStatus.getOrDefault("pending", 0L);
        long inProgress = byStatus.getOrDefault("in_progress", 0L);
        long resolved = byStatus.getOrDefault("resolved", 0L);
        long escalated = byStatus.getOrDefault("escalated", 0L);
        long closed = byStatus.getOrDefault("closed", 0L);

        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("total", total);
        pipeline.put("pending", pending);
        pipeline.put("inProgress", inProgress);
        pipeline.put("resolved", resolved);
        pipeline.put("escalated", escalated);
        pipeline.put("closed", closed);
        pipeline.put("activeBacklog", pending + inProgress + escalated);
        pipeline.put("resolutionRate", total > 0 ? Math.round((double)(resolved + closed) / total * 100) : 0);
        pipeline.put("computedAt", LocalDateTime.now().toString());
        return pipeline;
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    // TAT
    // ═════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * SLA position of the active backlog.
     *
     * <p>This is the one panel whose arithmetic CANNOT move into SQL: {@code TatCalculationService}
     * delegates to {@code BusinessHoursService}, which walks the interval day by day consulting the
     * {@code holidays} table and the configured business-hour window. So the optimisation here is to
     * the row set, not the loop — {@code findActiveTatRows} returns three columns for the active
     * complaints only (741 of 2497 rows measured) instead of 105 columns for all of them.
     *
     * <p>Identical {@code (filedAt, resolvedAt)} pairs are then collapsed and {@code calculateTat} is
     * called once per DISTINCT pair. This is exact rather than an approximation because the function
     * is pure in those two timestamps plus shared configuration. It is also a large win on this data:
     * 741 active rows carry only 38 distinct pairs, so the business-day walk runs 38 times instead of
     * 741 — QA seeding reuses timestamps heavily, and the collapse degrades gracefully to a no-op on
     * realistic data where pairs are unique.
     *
     * <p>One subtlety the collapse improves rather than harms: for an unresolved complaint
     * {@code calculateTat} substitutes {@code LocalDateTime.now()} for the end of the interval, so the
     * old per-row loop evaluated a slightly different "now" on every iteration. Computing once per
     * pair makes a single response internally consistent.
     */
    @Cacheable(value = "analytics-summary", key = "'senior-tat'")
    public Map<String, Object> getTatAnalytics() {
        List<DashboardProjections.TatRow> rows =
                complaintRepo.findActiveTatRows(CLOSED_STATUSES, PageRequest.of(0, TAT_ROW_CAP));

        long available = complaintRepo.countActiveForTat(CLOSED_STATUSES);
        if (available > rows.size()) {
            log.warn("TAT panel truncated: {} active complaints, {} sampled (cap {}). "
                            + "Reported figures cover the newest {} only.",
                    available, rows.size(), TAT_ROW_CAP, rows.size());
        }

        Map<DashboardProjections.TimestampPair, TatCalculationService.TatResult> tatByPair =
                new HashMap<>();

        long breached = 0;
        long atRisk = 0;
        long onTrack = 0;
        long totalElapsedDays = 0;
        int count = 0;

        for (DashboardProjections.TatRow row : rows) {
            TatCalculationService.TatResult tat = tatByPair.computeIfAbsent(
                    row.pair(), p -> tatService.calculateTat(p.filedAt(), p.resolvedAt()));

            if (tat.isBreached()) breached++;
            else if (tat.getPercentUsed() >= 80) atRisk++;
            else onTrack++;
            totalElapsedDays += tat.getBusinessDaysElapsed();
            count++;
        }

        log.debug("TAT: {} active rows collapsed to {} distinct timestamp pairs", rows.size(), tatByPair.size());

        Map<String, Object> result = new LinkedHashMap<>();
        // Kept as int, and sourced from the sampled list rather than the COUNT(*), so that this field
        // never disagrees with breached+atRisk+onTrack. The API contract and its tests read it as int.
        result.put("totalActive", rows.size());
        result.put("breached", breached);
        result.put("atRisk", atRisk);
        result.put("onTrack", onTrack);
        result.put("avgElapsedDays", count > 0 ? Math.round((double) totalElapsedDays / count * 10.0) / 10.0 : 0);
        result.put("breachRate", !rows.isEmpty() ? Math.round((double) breached / rows.size() * 100) : 0);
        result.put("computedAt", LocalDateTime.now().toString());
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    // BOTTLENECKS
    // ═════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Where the backlog is piling up: by department, category, priority and lack of an assignee.
     *
     * <p>Four grouped queries replace one whole-table hydrate and four stream passes.
     *
     * <p><b>{@code priority} is grouped in Java, on purpose.</b> The column holds six distinct stored
     * spellings — {@code MEDIUM} (2177), {@code medium} (252), {@code high} (39), {@code HIGH} (20),
     * {@code low} (19), {@code CRITICAL} (2) — and the case-insensitive collation reports only four.
     * A SQL {@code GROUP BY priority} would therefore MERGE {@code MEDIUM} with {@code medium} and
     * {@code HIGH} with {@code high}, silently changing this panel's output. The restrictive choice is
     * to preserve the buckets the previous code produced, so {@code findAllPriorityValues()} projects
     * the bare column (a covering index scan of one {@code VARCHAR(20)}) and the tally happens here in
     * a case-sensitive {@code HashMap}.
     *
     * <p>Merging those buckets may well be what the business wants, but it is a data-cleanup decision
     * with a visible effect on a management report, not something an optimisation should smuggle in.
     * If it is wanted, normalise the column with a migration and an application-level constraint; then
     * this method can become a one-line {@code GROUP BY} and the hazard disappears at the source
     * rather than being hidden behind a collation.
     *
     * <p>{@code department} has 3 distinct values under both collations, so it groups in SQL safely.
     */
    @Cacheable(value = "analytics-summary", key = "'senior-bottlenecks'")
    public Map<String, Object> getBottlenecks() {
        Map<String, Long> byDepartment = toCountMap(
                complaintRepo.countByDepartmentForStatuses(BACKLOG_STATUSES));

        // Grouped by category ID in SQL, then folded to NAME here. Two ids can share a display name
        // (and every unknown id becomes its own "Category-N"), so the fold must SUM on collision —
        // exactly what the previous groupingBy(getCategoryName(...)) did.
        Map<String, Long> byCategory = new HashMap<>();
        for (DashboardProjections.IdCount row : complaintRepo.countByCategoryForStatuses(BACKLOG_STATUSES)) {
            byCategory.merge(getCategoryName(row.groupId()), row.total(), Long::sum);
        }

        // See the class-level and method-level notes: case-sensitive by necessity.
        Map<String, Long> byPriority = new HashMap<>();
        for (String priority : complaintRepo.findAllPriorityValues()) {
            byPriority.merge(priority, 1L, Long::sum);
        }

        Map<String, Long> unassigned = toCountMap(
                complaintRepo.countUnassignedByDepartment(List.of("pending")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("backlogByDepartment", byDepartment);
        result.put("backlogByCategory", sortedTop(byCategory, 10));
        result.put("volumeByPriority", byPriority);
        result.put("unassignedByDepartment", unassigned);
        result.put("computedAt", LocalDateTime.now().toString());
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    // TREND
    // ═════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Filed / resolved / net per week for the last 12 weeks.
     *
     * <p>One two-column projection bounded to the 12-week window replaces the whole-table hydrate.
     * The week bucketing stays in Java rather than becoming 24 {@code COUNT(*)} queries, which was
     * measured to be about 9x SLOWER (1.58 s vs 0.18 s) because {@code resolved_at} has no index and
     * each resolved-count degrades to a full scan; see
     * {@code ComplaintRepository#findTrendTimestampsSince}.
     *
     * <p>Week boundaries are computed once, before the loop. The previous version called
     * {@code now.minusWeeks(...)} inside the loop against a {@code now} captured outside it, which was
     * consistent, but re-deriving boundaries per row is not — a single {@code now} for the whole
     * response keeps the 12 buckets contiguous and non-overlapping.
     */
    @Cacheable(value = "analytics-summary", key = "'senior-trend'")
    public List<Map<String, Object>> getWeeklyTrend() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusWeeks(TREND_WEEKS);

        List<DashboardProjections.TimestampPair> pairs =
                complaintRepo.findTrendTimestampsSince(windowStart);

        List<Map<String, Object>> weeks = new ArrayList<>(TREND_WEEKS);
        for (int w = TREND_WEEKS - 1; w >= 0; w--) {
            LocalDateTime weekStart = now.minusWeeks(w + 1);
            LocalDateTime weekEnd = now.minusWeeks(w);

            long filed = 0;
            long resolved = 0;
            for (DashboardProjections.TimestampPair p : pairs) {
                if (inWindow(p.filedAt(), weekStart, weekEnd)) filed++;
                if (inWindow(p.resolvedAt(), weekStart, weekEnd)) resolved++;
            }

            Map<String, Object> week = new LinkedHashMap<>();
            week.put("weekLabel", "W-" + w);
            week.put("filed", filed);
            week.put("resolved", resolved);
            week.put("net", filed - resolved);
            weeks.add(week);
        }
        return weeks;
    }

    /** Half-open {@code [start, end)}, matching the predicate this replaced. */
    private static boolean inWindow(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return value != null && !value.isBefore(start) && value.isBefore(end);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    // ENTITY PERFORMANCE
    // ═════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Per-regulated-entity volume, SLA breaches and resolutions, plus a department/status matrix.
     *
     * <p>Three grouped queries and one narrow projection replace a whole-table hydrate that was
     * traversed four times — one of those traversals calling {@code calculateTat} per row.
     *
     * <p>{@code entity_code} (31 distinct) and {@code department} (3) were both measured free of case
     * collisions, so they group in SQL.
     *
     * <p>The breach tally reuses the {@code TatRow} projection and the same distinct-pair collapse as
     * {@link #getTatAnalytics()}; it is a second pass over the same narrow row set rather than a
     * second hydrate. Rows with a null {@code entityCode} are dropped, as before.
     */
    @Cacheable(value = "analytics-summary", key = "'senior-entity-performance'")
    public Map<String, Object> getEntityPerformance() {
        Map<String, Long> volumeByEntity = toCountMap(complaintRepo.countByEntity());
        Map<String, Long> resolvedByEntity = toCountMap(
                complaintRepo.countByEntityForStatuses(RESOLVED_STATUSES));

        Map<DashboardProjections.TimestampPair, Boolean> breachedByPair = new HashMap<>();
        Map<String, Long> breachByEntity = new HashMap<>();
        for (DashboardProjections.TatRow row : complaintRepo.findActiveTatRows(
                CLOSED_STATUSES, PageRequest.of(0, TAT_ROW_CAP))) {
            if (row.entityCode() == null) continue;
            boolean breached = breachedByPair.computeIfAbsent(
                    row.pair(), p -> tatService.calculateTat(p.filedAt(), p.resolvedAt()).isBreached());
            if (breached) {
                breachByEntity.merge(row.entityCode(), 1L, Long::sum);
            }
        }

        // department -> status -> count, rebuilt from the flat two-level grouping SQL returns.
        Map<String, Map<String, Long>> statusByDept = new HashMap<>();
        for (DashboardProjections.DeptStatusCount row : complaintRepo.countByDepartmentAndStatus()) {
            statusByDept.computeIfAbsent(row.department(), d -> new HashMap<>())
                    .merge(row.status(), row.total(), Long::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("volumeByEntity", sortedTop(volumeByEntity, 10));
        result.put("breachByEntity", sortedTop(breachByEntity, 10));
        result.put("resolvedByEntity", sortedTop(resolvedByEntity, 10));
        result.put("statusByDepartment", statusByDept);
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Flattens {@code KeyCount} rows into a map, dropping null keys.
     *
     * <p>Null keys are already excluded by every query's {@code WHERE}, but a map built from SQL
     * output should not be able to acquire a {@code null} key if a query is later relaxed — the
     * previous Java filters dropped those rows and callers serialise this straight to JSON.
     * {@code Long::sum} on collision cannot trigger for a {@code GROUP BY} result and exists so a
     * future non-grouped source cannot silently lose a row.
     */
    private static Map<String, Long> toCountMap(List<DashboardProjections.KeyCount> rows) {
        Map<String, Long> out = new HashMap<>();
        for (DashboardProjections.KeyCount row : rows) {
            if (row.groupKey() != null) {
                out.merge(row.groupKey(), row.total(), Long::sum);
            }
        }
        return out;
    }

    private Map<String, Long> sortedTop(Map<String, Long> map, int limit) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }
}
