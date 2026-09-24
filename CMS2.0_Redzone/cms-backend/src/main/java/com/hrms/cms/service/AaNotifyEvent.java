package com.hrms.cms.service;

/**
 * The AA workflow events that raise a notification.
 *
 * <p>Owned by S3C and published early so S3B knows which keys exist and S3A knows what it may raise.
 * Where S3A ships its own event enum on {@code AaWorkflowNotifier}, this maps onto it by name via an
 * adapter rather than being replaced -- the persisted {@code eventCode} in AA_CITIZEN_NOTICE is an
 * audit value and must not shift because an interface was renamed.
 *
 * <p>{@code appellantKey} is null where the event is purely internal: an assignment between officers
 * is not the citizen's business, and sending a citizen a notice about it would leak workload
 * information while telling them nothing about their appeal.
 */
public enum AaNotifyEvent {

    /**
     * A new appeal has been filed and is now someone's work.
     *
     * <p>Officer-only. The appellant is acknowledged synchronously at the point of filing — they are shown
     * the appeal number — so a second citizen notice would duplicate it. Hence the null appellant key.
     *
     * <p>This constant did not exist, so {@code AaWorkflowEvent.FILED} mapped to null and the bell raised
     * at filing was silently dropped: the Designated Officer an appeal had just been placed with was never
     * told it existed.
     */
    APPEAL_FILED(null, "aa.notify.officer.appeal_filed"),
    APPEAL_ACCEPTED("aa.notify.appeal_accepted", "aa.notify.officer.appeal_accepted"),
    APPEAL_REJECTED("aa.notify.appeal_rejected", "aa.notify.officer.appeal_rejected"),
    ASSIGNED_TO_BENCH(null, "aa.notify.officer.assigned_to_bench"),
    HEARING_SCHEDULED("aa.notify.hearing_scheduled", "aa.notify.officer.hearing_scheduled"),
    HEARING_RESCHEDULED("aa.notify.hearing_rescheduled", "aa.notify.officer.hearing_rescheduled"),
    HEARING_ADJOURNED("aa.notify.hearing_adjourned", "aa.notify.officer.hearing_adjourned"),
    HEARING_OUTCOME_RECORDED(null, "aa.notify.officer.hearing_outcome_recorded"),
    ORDER_PASSED("aa.notify.order_passed", "aa.notify.officer.order_passed"),
    ORDER_CORRECTED("aa.notify.order_corrected", "aa.notify.officer.order_corrected"),
    APPEAL_DISMISSED("aa.notify.appeal_dismissed", "aa.notify.officer.appeal_dismissed"),
    APPEAL_REMANDED("aa.notify.appeal_remanded", "aa.notify.officer.appeal_remanded"),
    FORWARDED_TO_AUTHORITY(null, "aa.notify.officer.forwarded_to_authority"),
    SENT_BACK_REGISTRAR(null, "aa.notify.officer.sent_back_registrar"),
    REASSIGNED(null, "aa.notify.officer.reassigned"),
    ESCALATED_TO_TIER2(null, "aa.notify.officer.escalated_to_tier2"),
    HEARING_REMINDER("aa.notify.hearing_reminder", "aa.notify.officer.hearing_reminder"),
    SLA_REMINDER(null, "aa.notify.officer.sla_reminder");

    private final String appellantKey;
    private final String officerKey;

    AaNotifyEvent(String appellantKey, String officerKey) {
        this.appellantKey = appellantKey;
        this.officerKey = officerKey;
    }

    /** Translation key for the citizen-facing notice, or null when the event is internal only. */
    public String appellantKey() {
        return appellantKey;
    }

    /** Translation key for the staff bell notification. */
    public String officerKey() {
        return officerKey;
    }

    public boolean notifiesAppellant() {
        return appellantKey != null;
    }

    /** Tolerant lookup, so an unknown event from a caller degrades to null instead of throwing. */
    public static AaNotifyEvent fromName(String name) {
        if (name == null) return null;
        for (AaNotifyEvent event : values()) {
            if (event.name().equalsIgnoreCase(name.trim())) {
                return event;
            }
        }
        return null;
    }
}
