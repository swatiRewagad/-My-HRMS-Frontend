package com.rbi.cms.rules.dto;

import com.rbi.cms.rules.entity.RuleAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleHistoryResponse {

    private Long id;
    private Integer version;
    private String drlContent;
    private String changeReason;
    private String changedBy;
    private Instant changedAt;
    private RuleAction action;
}
