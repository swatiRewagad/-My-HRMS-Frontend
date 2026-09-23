package com.rbi.cms.search.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ComplaintSearchRequestDTO {

    private AdvancedSearchDTO advancedSearch;
    private FiltersDTO filters;

    private String statusCode;

    private String kpiCards;

    private String tabs;

    private Boolean unread;
    private Boolean withoutAttachments;
    private SearchFieldsDTO search;
}
