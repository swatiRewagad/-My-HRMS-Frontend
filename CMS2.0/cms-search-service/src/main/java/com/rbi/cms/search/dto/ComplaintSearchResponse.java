package com.rbi.cms.search.dto;

import com.rbi.cms.common.dto.PagedResponse;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintSearchResponse {

    private PagedResponse<ComplaintRowDTO> complaints;
    private KpiCountsDTO kpiCounts;
    private TabCountsDTO tabCounts;
}
