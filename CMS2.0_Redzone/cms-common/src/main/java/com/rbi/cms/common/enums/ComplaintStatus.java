package com.rbi.cms.common.enums;

import java.util.Locale;
import java.util.Optional;

public enum ComplaintStatus implements LabeledEnum {
    NEW("New"),
    ASSIGNED("Assigned"),
    IN_PROGRESS("In Progress"),
    UNDER_REVIEW("Under Review"),
    ESCALATED("Escalated"),
    RESOLVED("Resolved"),
    CLOSED("Closed"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    SENT_BACK("Sent Back"),
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
    SENT_BACK_TO_DO("Sent Back To DO"),
    DEPUTY_OMBUDSMAN_DECISION("Deputy Ombudsman Decision"),
    OMBUDSMAN_DECISION("Ombudsman Decision"),
    SENT_TO_DEPUTY_OMBUDSMAN("Sent To Deputy Ombudsman"),
    SENT_TO_REVIEWER("Sent To Reviewer"),
    SENT_TO_OMBUDSMAN("Sent To Ombudsman"),
    PENDING_OFFICE_HEAD_APPROVAL("Pending Office Head Approval"),
    SENT_TO_INCHARGE("Sent To Incharge"),
    SENT_TO_CLOSING_AUTHORITY("Sent To Closing Authority"),
    SENT_BACK_TO_INCHARGE("Sent Back To Incharge");

    private final String value;

    ComplaintStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

     public static String getKeyFromValue(String value) {
        for (ComplaintStatus status : ComplaintStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status.name();
            }
        }
        throw new IllegalArgumentException("No ComplaintStatus key found for value: " + value);
    }

    public static String getValueFromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        try {
            return ComplaintStatus.valueOf(key.toUpperCase().trim()).getValue();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No ComplaintStatus value found for key: " + key);
        }
    }

    /**
     * Tolerant lookup accepting either a constant name or a display label, in any case and with
     * spaces or underscores as the word separator — so {@code "in_progress"}, {@code "IN_PROGRESS"}
     * and {@code "In Progress"} all resolve.
     *
     * <p>The case-insensitivity is load-bearing rather than a convenience: {@code cms-backend}
     * persists status as lower snake_case ({@code in_progress}, {@code sent_back}) while the Java
     * services publish constant names, so a strict {@link #valueOf} rejects the entire backend
     * workflow. Returns empty instead of throwing, because the callers that need this are deciding
     * whether input is usable, not asserting that it is.
     */
    public static Optional<ComplaintStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (ComplaintStatus status : values()) {
            if (status.name().equals(normalized)
                    || status.value.toUpperCase(Locale.ROOT).replace(' ', '_').equals(normalized)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
