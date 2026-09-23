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

    private String complaintNumber;
    private Long id;
    private String statusCode;
    private String complainantName;
    private String complainantPhone;
    private String complainantEmail;
    private String filingType;
    private String entityCode;
    private String subject;
    private Long categoryId;
    private LocalDate filedAtStart;
    private LocalDate filedAtEnd;
    private String nodalOfficerName;
    private String fromEmailId;
}
