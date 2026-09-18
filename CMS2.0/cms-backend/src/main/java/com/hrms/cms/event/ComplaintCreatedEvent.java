package com.hrms.cms.event;

import com.hrms.cms.entity.Complaint;

/**
 * Raised inside the creation transaction and handled after it commits, so a consumer of
 * {@code complaint.ingested} can never read the event before the complaint row is visible.
 */
public record ComplaintCreatedEvent(Complaint complaint) {
}
