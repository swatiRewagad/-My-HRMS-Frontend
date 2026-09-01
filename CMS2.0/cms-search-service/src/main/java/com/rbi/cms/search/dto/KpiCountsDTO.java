package com.rbi.cms.search.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiCountsDTO {

    private long totalPendingComplaints;
    private long pendingWithMe;
    private long pendingWithRe;
    private long pendingAtMeetingSchedule;
    private long slaBreached;
    private long sla0To15Days;
    private long sla16To30Days;
}
