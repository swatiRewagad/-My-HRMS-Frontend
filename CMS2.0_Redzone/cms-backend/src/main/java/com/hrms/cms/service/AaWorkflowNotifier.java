package com.hrms.cms.service;

/**
 * Tells the people affected that something happened to an appeal.
 *
 * Published by S3A as a seam so the state machine can announce events without knowing how they are
 * delivered; S3C implements it. S3A ships a minimal implementation so the workflow functions before
 * S3C lands, and neither session blocks the other.
 *
 * Two delivery realities the implementation must respect, because they are not symmetric:
 *   - STAFF notifications work end to end today via NotificationService.send plus the STOMP bell.
 *   - CITIZEN notifications have NO transport. There is no working SMS or email gateway in this
 *     deployment. An appellant notification must therefore be PERSISTED as an auditable, retryable
 *     record; it must never be reported as delivered on the strength of a log line.
 *
 * Every method must be non-throwing from the caller's perspective. A notification failure must not roll
 * back a legitimate workflow transition — the appeal has moved whether or not the message went out, and
 * a notification outage that blocked all AA work would be a worse failure than a missed message.
 */
public interface AaWorkflowNotifier {

    /**
     * Notifies the appellant (a citizen) about {@code event} on {@code appealNumber}.
     *
     * Callers should gate on {@link AaWorkflowEvent#notifiesAppellant()} rather than deciding per
     * call site, so the policy lives in one place.
     */
    void notifyAppellant(String appealNumber, AaWorkflowEvent event);

    /** Notifies a staff user. Goes to the in-app bell. */
    void notifyOfficer(String userId, String appealNumber, AaWorkflowEvent event);

    /**
     * Notifies whoever now owns a complaint that has been remanded back out of AA.
     *
     * Separate from {@link #notifyOfficer} because the recipient is outside the AA module — an
     * RBIO/CEPC officer picking up a reopened complaint — and the message must reference the parent
     * complaint, not the appeal.
     */
    void notifyRemandTarget(String userId, String complaintNumber, String appealNumber);
}
