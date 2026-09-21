package com.hrms.cms.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thread counts for the simulation dashboard tiles.
 *
 * <p>Cached in Hazelcast, which serializes values reflectively — so this stays a plain POJO with a
 * no-arg constructor, matching {@code DashboardResponse} and the other cached DTOs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailStatsResponse {

    private int totalThreads;
    private long awaitingForm;
    private long completed;
}
