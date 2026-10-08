package com.hrms.cms.service;

import com.hrms.cms.dto.cepc.CepcCountSnapshot;
import com.hrms.cms.dto.cepc.CepcDashboardResponse;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.hrms.cms.dto.cepc.CepcCountSnapshot.or0;

/**
 * The thirteen numbers on the CEPC dashboard's KPI cards and tab badges.
 *
 * <p><b>One round trip, not thirteen.</b> A single conditional-aggregate query covers every badge. The Draft
 * badge used to need a second query against {@code STAFF_DRAFT} — half-finished intake forms with no complaint
 * behind them — but the Draft tab now lists complaints in status {@code DRAFT}, so it comes from the same pass
 * as the rest and cannot disagree with the grid beneath it.
 *
 * <p><b>The counts describe the caller's whole scope, never the current view.</b> They ignore the active
 * tab, KPI card and filters entirely, so clicking a KPI narrows the grid while every badge stays put. A
 * badge that moved as you filtered would be telling you the size of what you are already looking at.
 */
@Service
@RequiredArgsConstructor
public class CepcDashboardCountService {

    /**
     * Where the two forward SLA buckets divide, in days from today.
     *
     * <p>Forward-looking — "due in the next N days", not "breached N days ago". The breached complaints are
     * counted separately, so overlapping the two would double-count them.
     */
    private static final int SLA_BUCKET_1_END_DAYS = 15;
    private static final int SLA_BUCKET_2_END_DAYS = 30;

    private final ComplaintRepository complaintRepository;

    /** Both count blocks from one pass, so a request pays for the aggregate once. */
    public record Counts(CepcDashboardResponse.KpiCounts kpi, CepcDashboardResponse.TabCounts tab) {
    }

    /**
     * @param office the office scope the request carried, or null/blank for unscoped. Passed through rather
     *               than derived, for the same reason as {@code CepcSearchRequest.regionalOffice}: nothing
     *               server-side knows the caller's office yet.
     */
    public Counts counts(CepcComplaintSearchService.Caller caller, String office) {
        String me = caller == null || caller.userId() == null ? "" : caller.userId();
        String scopedOffice = office == null || office.isBlank() ? null : office.trim();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        CepcCountSnapshot s = complaintRepository.cepcDashboardCounts(
                me,
                scopedOffice,
                CepcComplaintSearchService.TERMINAL_STATUSES,
                CepcStatus.allSpellings(CepcStatus.COMPLAINT_WITHDRAWN),
                CepcStatus.allSpellings(CepcStatus.DRAFT),
                CepcStatus.allSpellings(CepcStatus.MEETING_SCHEDULED),
                CepcStatus.allSpellings(CepcStatus.INFORMATION_REQUIRED),
                CepcStatus.allSpellings(CepcStatus.SENT_TO_RBI),
                now,
                today.atStartOfDay(),
                today.plusDays(SLA_BUCKET_1_END_DAYS + 1L).atStartOfDay(),
                today.plusDays(SLA_BUCKET_2_END_DAYS + 1L).atStartOfDay());
        if (s == null) {
            s = CepcCountSnapshot.empty();
        }

        return new Counts(
                new CepcDashboardResponse.KpiCounts(
                        or0(s.totalPending()),
                        or0(s.pendingWithMe()),
                        or0(s.pendingWithRe()),
                        or0(s.pendingAtMeetingScheduled()),
                        or0(s.slaBreached()),
                        or0(s.sla0To15Days()),
                        or0(s.sla16To30Days())),
                new CepcDashboardResponse.TabCounts(
                        or0(s.draft()),
                        or0(s.meetingScheduled()),
                        or0(s.sentBackToMe()),
                        or0(s.sentToRe()),
                        or0(s.responseFromCp()),
                        or0(s.withdrawn())));
    }
}
