package com.rbi.cms.search.dto;

import lombok.Builder;

@Builder
public record TabCountsResponse(
        Long all,
        Long draft,
        Long meetingScheduled,
        Long sentBackToMe,
        Long sentToRe,
        Long responseFromRe,
        Long withdrawnComplaints
) {
    public TabCountsResponse {
        all = all != null ? all : 0L;
        draft = draft != null ? draft : 0L;
        meetingScheduled = meetingScheduled != null ? meetingScheduled : 0L;
        sentBackToMe = sentBackToMe != null ? sentBackToMe : 0L;
        sentToRe = sentToRe != null ? sentToRe : 0L;
        responseFromRe = responseFromRe != null ? responseFromRe : 0L;
        withdrawnComplaints = withdrawnComplaints != null ? withdrawnComplaints : 0L;
    }
}

