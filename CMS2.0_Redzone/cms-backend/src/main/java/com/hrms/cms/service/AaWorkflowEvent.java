package com.hrms.cms.service;

/**
 * The lifecycle events an AA appeal can emit, and the single vocabulary shared by notifications,
 * outbox publication and the audit trail.
 *
 * Owned by the state machine rather than by the notifier, because the state machine is what decides an
 * event has occurred. S3C implements delivery; it does not get to invent events.
 *
 * Each constant carries its own translation key and Kafka topic so a caller can never pair an event
 * with the wrong message. Topics follow the existing {@code appeal.<pastParticiple>} convention already
 * declared for {@code appeal.filed} in cms-common's KafkaTopics.
 */
public enum AaWorkflowEvent {

    FILED("aa.event.filed", "appeal.filed"),
    ACCEPTED("aa.event.accepted", "appeal.accepted"),
    REJECTED("aa.event.rejected", "appeal.rejected"),
    ASSIGNED_TO_REVIEWER("aa.event.assigned_to_reviewer", "appeal.assigned"),
    DOCUMENTS_REQUESTED("aa.event.documents_requested", "appeal.documents_requested"),
    HEARING_SCHEDULED("aa.event.hearing_scheduled", "appeal.hearing_scheduled"),
    HEARING_RESCHEDULED("aa.event.hearing_rescheduled", "appeal.hearing_rescheduled"),
    ESCALATED_TO_TIER2("aa.event.escalated_to_tier2", "appeal.escalated"),
    FORWARDED_TO_AUTHORITY("aa.event.forwarded_to_authority", "appeal.forwarded"),
    SENT_BACK("aa.event.sent_back", "appeal.sent_back"),
    ORDER_PASSED("aa.event.order_passed", "appeal.order_passed"),
    REMANDED("aa.event.remanded", "appeal.remanded"),
    DISMISSED("aa.event.dismissed", "appeal.dismissed"),
    CLOSED("aa.event.closed", "appeal.closed"),
    REOPENED("aa.event.reopened", "appeal.reopened"),
    REASSIGNED("aa.event.reassigned", "appeal.reassigned");

    private final String messageKey;
    private final String topic;

    AaWorkflowEvent(String messageKey, String topic) {
        this.messageKey = messageKey;
        this.topic = topic;
    }

    /** Translation key for the notification body. NEVER an English literal. */
    public String getMessageKey() {
        return messageKey;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * True when the appellant — a citizen, not a staff user — must be told.
     *
     * Deliberately narrow: a citizen should hear about decisions that affect them, not about internal
     * routing. Telling them an appeal moved between two officers is noise that erodes attention to the
     * messages that matter.
     */
    public boolean notifiesAppellant() {
        return switch (this) {
            case FILED, ACCEPTED, REJECTED, HEARING_SCHEDULED, HEARING_RESCHEDULED,
                 ORDER_PASSED, REMANDED, DISMISSED, CLOSED -> true;
            default -> false;
        };
    }
}
