package com.rbi.cms.ingestion.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeoUserResponse {

    private Long id;
    private String userId;
    private String displayName;
    private String email;
    private Boolean isActive;
    private Boolean isOnLeave;
    private Integer maxThreshold;
    private Integer currentAssignedCount;
    private Integer sortOrder;
}
