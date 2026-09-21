package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One step in a past complaint's history.
 *
 * <p>Deliberately not {@link ComplaintTimelineItem}: this one also names who acted, and widening that
 * type to match would add a key to an unrelated endpoint's payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PastComplaintTimelineEntry {

    private String action;
    /** Resolved display name of the actor, not the raw user id. */
    private String performedBy;
    private String remarks;
    private String fromStatus;
    private String toStatus;
    /** Empty string when the entry has no recorded time. */
    private String timestamp;
}
