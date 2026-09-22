package com.hrms.cms.controller;

import com.hrms.cms.service.report.CrpcReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The CRPC operational reports (UST777, and the surface the CRPC screen has always called).
 *
 * <h2>Why this endpoint is being added now</h2>
 * {@code crpc-reports.component.ts} has called {@code GET /api/v1/crpc/reports} since it was written.
 * The endpoint never existed. Its error handler responded by filling the table with fifteen rows of
 * {@code Math.random()} — so an RBI-facing report screen has only ever displayed fabricated figures, and
 * its CSV export wrote those figures to a file named after a real date range. The Excel path was doubly
 * masked: its own error handler fell back to CSV-ing the same invented numbers.
 *
 * <p>The mock fallback is removed in the same change. Leaving it while adding a real endpoint would keep
 * a code path that silently substitutes fiction whenever the backend has a bad minute.
 *
 * <h2>Scope</h2>
 * Three of the seven report types the client offers are implemented from real tables. The remaining four
 * return an explicit {@code implemented: false} rather than plausible numbers, because a report that
 * cannot be computed truthfully must say so — see {@link CrpcReportService} for which and why.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/crpc/reports")
@RequiredArgsConstructor
public class CrpcReportsController {

    private final CrpcReportService crpcReportService;

    /**
     * Runs one CRPC report.
     *
     * <p>Returns {@code {reportType, implemented, columns, data, ...}}. The client accepts either a bare
     * array or {@code {data: [...]}}, so this stays wire-compatible with the existing component while
     * adding the {@code implemented} flag it needs to stop inventing rows.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> generate(
            @RequestParam String reportType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String schemeVersion,
            @RequestParam(required = false) String office) {

        LocalDate from = parseDate(dateFrom, "dateFrom");
        LocalDate to = parseDate(dateTo, "dateTo");

        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("dateTo cannot be before dateFrom.");
        }

        CrpcReportService.ReportResult result =
                crpcReportService.generate(reportType, from, to, schemeVersion, office);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reportType", reportType);
        response.put("implemented", result.implemented());
        response.put("message", result.message());
        response.put("messageKey", result.messageKey());
        response.put("rowCount", result.rows().size());
        response.put("data", result.rows());
        return ResponseEntity.ok(response);
    }

    /** The report types this server can actually compute, for the client to offer. */
    @GetMapping("/available")
    public ResponseEntity<List<Map<String, Object>>> available() {
        return ResponseEntity.ok(crpcReportService.availableReports());
    }

    private LocalDate parseDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be a date in yyyy-MM-dd form.");
        }
    }
}
