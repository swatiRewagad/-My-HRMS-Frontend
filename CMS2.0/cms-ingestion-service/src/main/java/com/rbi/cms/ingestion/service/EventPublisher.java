package com.rbi.cms.ingestion.service;

import com.rbi.cms.common.event.ComplaintEvent;

public interface EventPublisher {
    void publishComplaintEvent(String topic, String key, ComplaintEvent event);
}
