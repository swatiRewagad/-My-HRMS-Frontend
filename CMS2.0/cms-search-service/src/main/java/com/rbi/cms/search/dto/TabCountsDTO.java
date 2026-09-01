package com.rbi.cms.search.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TabCountsDTO {

    private long all;
    private long draft;
    private long meetingScheduled;
    private long sentBackToMe;
}
