package com.rbi.cms.common.config;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String COMPLAINT_INGESTED = "complaint.ingested";
    public static final String COMPLAINT_ASSIGNED = "complaint.assigned";
    public static final String COMPLAINT_IN_PROGRESS = "complaint.inprogress";
    public static final String COMPLAINT_ESCALATED = "complaint.escalated";
    public static final String COMPLAINT_RESOLVED = "complaint.resolved";
    public static final String COMPLAINT_CLOSED = "complaint.closed";
    public static final String COMPLAINT_READ = "complaint.read";
    public static final String COMPLAINT_DLQ = "complaint.dlq";

    /**
     * Request to dispatch one outbound notification. Named for a request rather than in the
     * {@code complaint.<past-tense>} form of the topics above because it is a command in a different
     * domain — nothing has been dispatched yet when it is published, so past tense would be untrue.
     */
    public static final String NOTIFICATION_REQUESTED = "notification.requested";

    /**
     * Separate from {@link #COMPLAINT_DLQ}, which collects complaint-event processing failures and is
     * drained by whoever owns those. Mixing dispatch commands into it would put records there that its
     * operators cannot interpret.
     */
    public static final String NOTIFICATION_DLQ = "notification.dlq";
}
