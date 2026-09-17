package com.rbi.cms.search.dto;

import com.rbi.cms.common.dto.PagedResponse;
import lombok.Builder;

@Builder
public record ComplaintSearchResponse(
        PagedResponse<ComplaintResponse> complaints,

        KpiCountsResponse kpiCounts,

        TabCountsResponse tabCounts
) { }
