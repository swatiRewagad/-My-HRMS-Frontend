package com.rbi.cms.search.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AdvancedSearchDTO {

    private String complainantName;
    private String status;
    private String entityName;
    private String complaintCategory;
    private LocalDate createdDateStart;
    private LocalDate createdDateEnd;
    private LocalDate resolvedDateStart;
    private LocalDate resolvedDateEnd;
    private String priority;
    private String severity;
    private String region;
    private String branchCode;
    private String escalationLevel;
}
