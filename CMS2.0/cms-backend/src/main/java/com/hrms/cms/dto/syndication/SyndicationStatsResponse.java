package com.hrms.cms.dto.syndication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Counters for the CRPC queue header tiles.
 *
 * <p>{@code pendingCount}, {@code duplicateCount} and {@code ignoredCount} are hardcoded to zero —
 * nothing tracks those states yet, but the tiles exist, so the keys are kept rather than removed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicationStatsResponse {

    private long totalDrafts;
    private long pendingCount;
    private long assignedCount;
    private long inProgressCount;
    private long convertedCount;
    private long duplicateCount;
    private long ignoredCount;
    private long activeDeoCount;
}
