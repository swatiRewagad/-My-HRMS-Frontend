package com.rbi.cms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentFact {

    private String complaintId;
    private String category;
    private String priority;
    private String jurisdictionCode;
    private Double amountInvolved;

    private String assignedTeam;
    private String assignedOfficer;
    private boolean escalated;
    private int escalationLevel;
}
