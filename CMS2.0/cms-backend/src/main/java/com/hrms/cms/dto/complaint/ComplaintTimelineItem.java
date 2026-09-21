package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintTimelineItem {

    private String fromStatus;
    private String toStatus;
    private String action;
    private String timestamp;
    private String remarks;
}
