package com.hrms.cms.service.report;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the CRPC operational reports from real data (UST777).
 *
 * <h2>What this replaces</h2>
 * The CRPC report screen called an endpoint that did not exist, and on failure filled itself with fifteen
 * rows of {@code Math.random()}. Every figure that screen has ever displayed was invented, and its CSV
 * export wrote those figures to a file named after a real date range. This computes three of the seven
 * report types from the database.
 *
 * <h2>Why only three, and why the other four say so out loud</h2>
 * The four unimplemented reports need data this schema does not carry:
 * <ul>
 *   <li><b>deo-productivity</b> and <b>reviewer-workload</b> need per-officer processing counts and
 *       average handling time. There is no per-officer work-item table with timestamps to derive
 *       {@code avgTime} from; {@code AUDIT_LOG} records actions but not the assignment intervals these
 *       columns describe.</li>
 *   <li><b>sla-compliance</b> needs a per-complaint SLA verdict. The SLA monitor writes against a
 *       different table from the one these complaints live in (the two-schema split), so a compliance
 *       rate computed here would silently omit most rows — worse than no number, because a plausible
 *       percentage invites a decision.</li>
 *   <li><b>transfer-summary</b> needs from/to office pairs with an approval outcome per transfer. Office
 *       transfers are Phase 2 and no such table exists yet.</li>
 * </ul>
 * Each returns {@code implemented: false} with a reason. A report that cannot be computed truthfully must
 * say so rather than produce a number somebody will act on — this is a regulator-facing surface.
 *
 * <h2>Queries are native but never concatenate user input</h2>
 * Every filter is a bound parameter. The report type selects a fixed query from a switch, so the only
 * caller-controlled values reaching SQL are typed dates and bound strings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrpcReportService {

    private final EntityManager entityManager;

    /** The outcome of a report run. {@code implemented} false means no data could be computed. */
    public record ReportResult(boolean implemented, String message, String messageKey,
                               List<Map<String, Object>> rows) {

        static ReportResult of(List<Map<String, Object>> rows) {
            return new ReportResult(true, null, null, rows);
        }

        static ReportResult notImplemented(String reason) {
            return new ReportResult(false, reason, "crpc.reports.error_not_available", List.of());
        }
    }

    public ReportResult generate(String reportType, LocalDate from, LocalDate to,
                                 String schemeVersion, String office) {
        if (reportType == null || reportType.isBlank()) {
            throw new IllegalArgumentException("A reportType is required.");
        }

        return switch (reportType.trim().toLowerCase()) {
            case "daily-intake" -> dailyIntake(from, to, office);
            case "closure-analysis" -> closureAnalysis(from, to, schemeVersion, office);
            case "entity-wise" -> entityWise(from, to, office);

            case "deo-productivity" -> ReportResult.notImplemented(
                    "DEO productivity needs per-officer processing counts and average handling time. "
                            + "No per-officer work-item table with assignment timestamps exists yet, so "
                            + "these figures cannot be computed from the current schema.");
            case "reviewer-workload" -> ReportResult.notImplemented(
                    "Reviewer workload needs per-reviewer assigned/completed counts. The same per-officer "
                            + "work-item data is missing.");
            case "sla-compliance" -> ReportResult.notImplemented(
                    "SLA compliance needs a per-complaint SLA verdict. The SLA monitor writes against a "
                            + "different complaint table from these rows, so a rate computed here would "
                            + "silently omit most complaints.");
            case "transfer-summary" -> ReportResult.notImplemented(
                    "Transfer summary needs from/to office pairs with an approval outcome. Inter-office "
                            + "transfers are Phase 2 and no such table exists yet.");

            default -> throw new IllegalArgumentException("Unknown CRPC report type: " + reportType);
        };
    }

    /**
     * Intake counts per day, split by the channel the complaint arrived through.
     *
     * <p>Column keys match what the client's hardcoded config expects: {@code date, email, physical,
     * cpgrams, portal, total}. The channel comes from {@code filing_type}; anything that is not one of the
     * three named channels counts as {@code portal}, which is the default filing route.
     */
    private ReportResult dailyIntake(LocalDate from, LocalDate to, String office) {
        StringBuilder sql = new StringBuilder(
                "SELECT DATE(c.created_at) AS d, "
                        + " SUM(CASE WHEN UPPER(COALESCE(c.filing_type,'')) LIKE '%EMAIL%' THEN 1 ELSE 0 END) AS email_ct, "
                        + " SUM(CASE WHEN UPPER(COALESCE(c.filing_type,'')) LIKE '%PHYSICAL%' "
                        + "        OR UPPER(COALESCE(c.filing_type,'')) LIKE '%LETTER%' THEN 1 ELSE 0 END) AS physical_ct, "
                        + " SUM(CASE WHEN UPPER(COALESCE(c.filing_type,'')) LIKE '%CPGRAMS%' THEN 1 ELSE 0 END) AS cpgrams_ct, "
                        + " SUM(CASE WHEN UPPER(COALESCE(c.filing_type,'')) NOT LIKE '%EMAIL%' "
                        + "        AND UPPER(COALESCE(c.filing_type,'')) NOT LIKE '%PHYSICAL%' "
                        + "        AND UPPER(COALESCE(c.filing_type,'')) NOT LIKE '%LETTER%' "
                        + "        AND UPPER(COALESCE(c.filing_type,'')) NOT LIKE '%CPGRAMS%' THEN 1 ELSE 0 END) AS portal_ct, "
                        + " COUNT(*) AS total_ct "
                        + "FROM COMPLAINTS c WHERE 1=1");
        appendDateRange(sql, "c.created_at", from, to);
        appendOffice(sql, office);
        sql.append(" GROUP BY DATE(c.created_at) ORDER BY d DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindDateRange(query, from, to);
        bindOffice(query, office);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] cells = (Object[]) row;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("date", asText(cells[0]));
            out.put("email", asLong(cells[1]));
            out.put("physical", asLong(cells[2]));
            out.put("cpgrams", asLong(cells[3]));
            out.put("portal", asLong(cells[4]));
            out.put("total", asLong(cells[5]));
            rows.add(out);
        }
        return ReportResult.of(rows);
    }

    /**
     * Closure counts grouped by the clause actually cited.
     *
     * <p>Only genuinely closed complaints with a recorded clause are counted. Complaints closed without a
     * clause are excluded rather than bucketed as "unknown": that population is a known data-quality
     * problem, and folding it into a clause report would present it as a clause.
     *
     * <p>{@code percentage} is computed over the rows this report returns, so the column sums to 100.
     */
    private ReportResult closureAnalysis(LocalDate from, LocalDate to, String schemeVersion, String office) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.closure_clause, COUNT(*) AS ct, "
                        + " MIN(COALESCE(c.entity_code,'')) AS any_entity "
                        + "FROM COMPLAINTS c "
                        + "WHERE c.closed_at IS NOT NULL "
                        + "  AND c.closure_clause IS NOT NULL AND c.closure_clause <> ''");
        appendDateRange(sql, "c.closed_at", from, to);
        appendOffice(sql, office);
        sql.append(" GROUP BY c.closure_clause ORDER BY ct DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindDateRange(query, from, to);
        bindOffice(query, office);

        List<Object[]> raw = castRows(query.getResultList());
        long total = raw.stream().mapToLong(r -> asLong(r[1])).sum();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] cells : raw) {
            long count = asLong(cells[1]);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("clauseRef", asText(cells[0]));
            out.put("count", count);
            out.put("percentage", total == 0 ? "0.0%"
                    : String.format("%.1f%%", (count * 100.0) / total));
            // The Scheme version is a property of the Scheme in force, not of the row. It is echoed from
            // the request so the report states which version it was run for, rather than implying the
            // database recorded one per complaint.
            out.put("schemeVersion", schemeVersion == null ? "" : schemeVersion);
            out.put("entityType", asText(cells[2]));
            rows.add(out);
        }
        return ReportResult.of(rows);
    }

    /** Per-entity totals with resolved/pending split and mean days to resolution. */
    private ReportResult entityWise(LocalDate from, LocalDate to, String office) {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(NULLIF(c.entity_name,''), COALESCE(c.entity_code,'Unknown')) AS name, "
                        + " COUNT(*) AS total_ct, "
                        + " SUM(CASE WHEN c.closed_at IS NOT NULL THEN 1 ELSE 0 END) AS resolved_ct, "
                        + " SUM(CASE WHEN c.closed_at IS NULL THEN 1 ELSE 0 END) AS pending_ct, "
                        + " AVG(CASE WHEN c.closed_at IS NOT NULL "
                        + "     THEN TIMESTAMPDIFF(DAY, c.created_at, c.closed_at) END) AS avg_days "
                        + "FROM COMPLAINTS c WHERE 1=1");
        appendDateRange(sql, "c.created_at", from, to);
        appendOffice(sql, office);
        sql.append(" GROUP BY name ORDER BY total_ct DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindDateRange(query, from, to);
        bindOffice(query, office);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] cells : castRows(query.getResultList())) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("entityName", asText(cells[0]));
            out.put("totalComplaints", asLong(cells[1]));
            out.put("resolved", asLong(cells[2]));
            out.put("pending", asLong(cells[3]));
            // Null when nothing in this group has closed yet — reported as null, not as 0, because
            // "no complaint has closed" and "complaints close the same day" are different facts.
            out.put("avgResolutionDays", cells[4] == null
                    ? null : String.format("%.1f", ((Number) cells[4]).doubleValue()));
            rows.add(out);
        }
        return ReportResult.of(rows);
    }

    /** What the client may offer, with a reason attached to each report it cannot run. */
    public List<Map<String, Object>> availableReports() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(reportMeta("daily-intake", "Daily Intake Report", true, null));
        out.add(reportMeta("closure-analysis", "Closure Analysis Report", true, null));
        out.add(reportMeta("entity-wise", "Entity-Wise Report", true, null));
        out.add(reportMeta("deo-productivity", "DEO Productivity Report", false,
                "Needs per-officer processing counts and handling time, which are not recorded yet."));
        out.add(reportMeta("reviewer-workload", "Reviewer Workload Report", false,
                "Needs per-reviewer assignment data, which is not recorded yet."));
        out.add(reportMeta("sla-compliance", "SLA Compliance Report", false,
                "Needs a per-complaint SLA verdict; the SLA monitor covers a different complaint table."));
        out.add(reportMeta("transfer-summary", "Transfer Summary Report", false,
                "Inter-office transfers are Phase 2; no transfer records exist yet."));
        return out;
    }

    private Map<String, Object> reportMeta(String id, String name, boolean implemented, String reason) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", id);
        meta.put("name", name);
        meta.put("implemented", implemented);
        meta.put("unavailableReason", reason);
        return meta;
    }

    private void appendDateRange(StringBuilder sql, String column, LocalDate from, LocalDate to) {
        if (from != null) {
            sql.append(" AND ").append(column).append(" >= :fromTs");
        }
        if (to != null) {
            sql.append(" AND ").append(column).append(" < :toTs");
        }
    }

    private void bindDateRange(Query query, LocalDate from, LocalDate to) {
        if (from != null) {
            query.setParameter("fromTs", from.atStartOfDay());
        }
        if (to != null) {
            // Exclusive upper bound on the following midnight, so the whole of the To day is included.
            // A `<= to.atStartOfDay()` would silently drop everything filed after midnight on the last
            // day of the range.
            query.setParameter("toTs", to.plusDays(1).atStartOfDay());
        }
    }

    private void appendOffice(StringBuilder sql, String office) {
        if (office != null && !office.isBlank()) {
            sql.append(" AND (c.department = :office OR c.rbio_office_code = :office)");
        }
    }

    private void bindOffice(Query query, String office) {
        if (office != null && !office.isBlank()) {
            query.setParameter("office", office.trim());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> castRows(List<?> resultList) {
        return (List<Object[]>) resultList;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate().toString();
        }
        return value.toString();
    }

    private long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
