package com.rbi.cms.search.dto;

import lombok.Builder;

@Builder
public record KpiCountsResponse(
        Long totalPendingComplaints,
        Long pendingWithMe,
        Long pendingWithRe,
        Long pendingAtMeetingScheduled,
        Long slaBreached,
        Long sla0To15Days,
        Long sla16To30Days
) {
    public KpiCountsResponse {
        totalPendingComplaints = totalPendingComplaints != null ? totalPendingComplaints : 0L;
        pendingWithMe = pendingWithMe != null ? pendingWithMe : 0L;
        pendingWithRe = pendingWithRe != null ? pendingWithRe : 0L;
        pendingAtMeetingScheduled = pendingAtMeetingScheduled != null ? pendingAtMeetingScheduled : 0L;
        slaBreached = slaBreached != null ? slaBreached : 0L;
        sla0To15Days = sla0To15Days != null ? sla0To15Days : 0L;
        sla16To30Days = sla16To30Days != null ? sla16To30Days : 0L;
    }
}
