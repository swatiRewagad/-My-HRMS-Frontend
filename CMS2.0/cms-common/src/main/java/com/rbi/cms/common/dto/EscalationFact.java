package com.rbi.cms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalationFact {

    private String complaintId;
    private String category;
    private String priority;
    private double slaPercentElapsed;
    private double amountInvolved;
    private int currentDaysOpen;

    private int escalationLevel;
    private String escalationAction;
    private String escalationMessage;
}
