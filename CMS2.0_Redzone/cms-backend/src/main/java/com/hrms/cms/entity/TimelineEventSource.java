package com.hrms.cms.entity;

/**
 * Whether a {@link ComplaintTimeline} row records something a person did or something the system
 * derived (UST848).
 *
 * The distinction matters for the RE Activity Status history: an activity badge that moved because
 * the entity uploaded a document is evidence of engagement, whereas one that moved to OVERDUE
 * because a clock expired is not, and staff reading the history need to tell those apart at a
 * glance without inferring it from the actor string.
 */
public enum TimelineEventSource {

    /** A user performed the action. performedBy identifies them. */
    MANUAL,

    /** The system derived the entry — a scheduled sweep, a clock expiry, or a side effect. */
    AUTOMATIC
}
