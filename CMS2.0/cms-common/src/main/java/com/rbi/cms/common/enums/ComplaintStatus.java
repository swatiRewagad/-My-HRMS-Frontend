package com.rbi.cms.common.enums;

public enum ComplaintStatus {
    DRAFT("Draft"),
    NEW_COMPLAINT("New Complaint"),
    AWARD_PASSED("Award Passed"),
    INFORMATION_REQUIRED("Information Required"),
    SENT_TO_RBI("Sent To RBI"),
    SENT_TO_OTHER_REGULATED_BODIES("Sent To Other Regulated Bodies"),
    SENT_TO_OTHER_DEPARTMENTS("Sent To Other Departments"),
    SENT_TO_OTHER_OFFICE("Sent To Other Office"),
    COMPLAINT_REOPEN("Complaint Re Open"),
    COMPLAINT_REJECTED("Complaint Rejected"),
    COMPLAINT_SETTLED("Complaint Settled"),
    COMPLAINT_WITHDRAWN("Complaint Withdrawn"),
    COMPLAINT_CLOSED("Complaint Closed"),
    MEETING_SCHEDULED("Meeting Scheduled"),
    ADVISORY_COMPLIED("Advisory Complied"),
    SENT_BACK_TO_DEPUTY_OMBUDSMAN("Sent Back To Deputy Ombudsman"),
    SENT_BACK_TO_REVIEWER("Sent Back To Reviewer"),
    SENT_BACK_TO_DO("Sent Back To Do"),
    DEPUTY_OMBUDSMAN_DECISION("Deputy Ombudsman Decision"),
    OMBUDSMAN_DECISION("Ombudsman Decision"),
    SENT_TO_DEPUTY_OMBUDSMAN("Sent To Deputy Ombudsman"),
    SENT_TO_REVIEWER("Sent To Reviewer"),
    SENT_TO_OMBUDSMAN("Sent To Ombudsman");

    private final String value;

    ComplaintStatus(String value) { this.value = value; }

    public String getValue() { return this.value; }
}
