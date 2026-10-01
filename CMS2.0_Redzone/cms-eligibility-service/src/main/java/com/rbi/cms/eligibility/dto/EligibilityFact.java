package com.rbi.cms.eligibility.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityFact {

    private boolean courtMatterPending;
    private boolean approachedBank;
    private boolean waitingPeriodCompleted;
    private boolean duplicateComplaint;
    private String jurisdictionCode;
    private String complaintCategory;

    private boolean eligible;
    private String reasonCode;
    private String reasonMessage;
}
