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
    public static final String COMPLAINT_DLQ = "complaint.dlq";
}
