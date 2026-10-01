package com.hrms.cms.repository.projection;

import java.time.LocalDateTime;

/**
 * Constructor-expression carriers for the senior-dashboard aggregates.
 *
 * <p>These exist so the dashboard selects grouped counts and narrow tuples instead of hydrating
 * {@code Complaint} entities. {@code COMPLAINTS} has 105 columns including six {@code TEXT} bodies
 * (description, relief_sought, eligibility_timeline, advisory_text, triage_flags,
 * reopen_justification), so a whole-table read costs far more heap than the row count suggests.
 *
 * <p>Records rather than Spring Data interface projections, and JPQL {@code SELECT new} rather than
 * native SQL, because the deployed profiles run {@code OracleDialect} while local development runs
 * MySQL. Native grouping SQL would compile here and fail in the one environment that matters.
 */
public final class DashboardProjections {

    private DashboardProjections() {
    }

    /** A grouping key and its count. */
    public record KeyCount(String groupKey, long total) {
    }

    /** A category-id grouping key and its count; the name lookup stays outside SQL. */
    public record IdCount(Long groupId, long total) {
    }

    /** A two-level grouping: department then status. */
    public record DeptStatusCount(String department, String status, long total) {
    }

    /**
     * The only three fields TAT needs, for one active complaint.
     *
     * <p>TAT cannot be expressed in SQL: {@code BusinessHoursService} walks day by day, consults the
     * {@code holidays} table and reads configured business hours. So the row set is narrowed instead —
     * to the active complaints only, three columns wide — and identical {@code (filedAt, resolvedAt)}
     * pairs are then collapsed before {@code calculateTat} is called.
     *
     * <p>Collapsing is exact, not an approximation: {@code calculateTat} is a pure function of its two
     * timestamps and the shared holiday/business-hour configuration, so two complaints with equal
     * timestamps cannot yield different results.
     */
    public record TatRow(String entityCode, LocalDateTime filedAt, LocalDateTime resolvedAt) {

        /** The cache key for a collapsed TAT computation. */
        public TimestampPair pair() {
            return new TimestampPair(filedAt, resolvedAt);
        }
    }

    /** A distinct {@code (filedAt, resolvedAt)} pair — the unit TAT is actually computed for. */
    public record TimestampPair(LocalDateTime filedAt, LocalDateTime resolvedAt) {
    }
}
