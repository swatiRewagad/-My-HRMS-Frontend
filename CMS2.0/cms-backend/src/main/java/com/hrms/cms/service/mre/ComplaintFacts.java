package com.hrms.cms.service.mre;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ComplaintFacts {

    private final String entityCode;
    private final String entityType;
    private final String categoryCode;

    private final boolean priorReComplaint;
    private final LocalDate reComplaintDate;
    private final String reComplaintReference;
    private final boolean reRepliedAndDissatisfied;

    private final LocalDate filingDate;

    private final boolean sameGrievancePendingOrDecided;

    private final LocalDate reLastCommunicationDate;
}
