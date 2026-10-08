package com.hrms.cms.dto.cepc;

import java.util.List;

/**
 * The CEPC dashboard payload: one page of the grid plus the KPI and tab badge counts.
 *
 * <p>Field names match {@code models/cepc.model.ts} character for character, because the Angular
 * components read them directly with no adapter layer. Two spellings in particular are load-bearing:
 * {@code pendingAtMeetingScheduled} (the old service shipped {@code pendingAtMeetingSchedule}, so that card
 * always rendered blank) and {@code sla0To15Days} with a capital {@code T}.
 *
 * @param complaints the requested page
 * @param kpiCounts  the five KPI cards' numbers, the last of which renders three metrics
 * @param tabCounts  the tab badges
 */
public record CepcDashboardResponse(
        Page complaints,
        KpiCounts kpiCounts,
        TabCounts tabCounts) {

    /**
     * A page of grid rows.
     *
     * <p>Hand-rolled rather than serialising Spring's {@code Page}: the frontend reads {@code page}/
     * {@code size}/{@code last} at the top level, whereas {@code Page} nests them under {@code pageable}
     * and emits a deprecation warning when serialised directly.
     *
     * <p>{@code content} holds {@link CepcComplaintRow} on every tab, Draft included: Draft lists complaints
     * in status {@code DRAFT}, not part-filled {@code STAFF_DRAFT} intake forms, so there is no second row
     * shape. It stays {@code List<?>} because the frontend contract is the JSON, not the Java type.
     */
    public record Page(
            List<?> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last) {

        public static Page of(List<?> content, int page, int size, long totalElements) {
            int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
            return new Page(content, page, size, totalElements, totalPages, page >= totalPages - 1);
        }
    }

    /**
     * The KPI card numbers.
     *
     * <p>The two SLA windows are forward-looking — complaints falling DUE in the next 0-15 and 16-30 days.
     * Deliberately not "overdue by up to 15 days", which is how the old service read them and which made
     * every breached complaint appear in both {@code slaBreached} and a window, so the three numbers on that
     * card did not add up.
     */
    public record KpiCounts(
            long totalPendingComplaints,
            long pendingWithMe,
            long pendingWithRe,
            long pendingAtMeetingScheduled,
            long slaBreached,
            long sla0To15Days,
            long sla16To30Days) {
    }

    /**
     * The tab badge numbers.
     *
     * <p>No {@code all} member: the "All" tab shows no badge, and its count is already
     * {@code complaints.totalElements} whenever that tab is the active one.
     */
    public record TabCounts(
            long draft,
            long meetingScheduled,
            long sentBackToMe,
            long sentToRe,
            /** Renamed with the tab: the response comes back from the contact person, not the entity. */
            long responseFromCp,
            long withdrawnComplaints) {
    }
}
