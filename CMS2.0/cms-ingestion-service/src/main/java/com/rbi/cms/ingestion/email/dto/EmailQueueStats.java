package com.rbi.cms.ingestion.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailQueueStats {

    private long totalDrafts;
    private long pendingCount;
    private long assignedCount;
    private long inProgressCount;
    private long convertedCount;
    private long duplicateCount;
    private long ignoredCount;
    private long activeDeoCount;
}
