package com.rbi.cms.ingestion.email.dto;

import com.rbi.cms.ingestion.email.entity.EmailIgnoreList.PatternType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IgnoreListResponse {

    private Long id;
    private String emailPattern;
    private PatternType patternType;
    private String reason;
    private String addedBy;
    private Boolean isActive;
    private Instant createdAt;
}
