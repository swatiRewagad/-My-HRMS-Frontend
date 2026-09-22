package com.hrms.cms.service;

import java.util.List;

/**
 * Thrown when an action is legitimate for the caller's role but not from the appeal's current status —
 * PASS_ORDER on an appeal that has not yet been accepted, say, or any ordinary action on a disposed one.
 *
 * A distinct type rather than IllegalArgumentException so the handler can answer 409 CONFLICT: the
 * request is well-formed and the caller is authorised, but the appeal has moved on. A 400 would suggest
 * the client sent something malformed, and a 403 would suggest a permissions problem — both would send
 * an operator looking in the wrong place.
 *
 * Carries the actions that ARE available from the current status, so the client can correct itself in
 * one round trip instead of guessing. That list comes from the same table that refused the request.
 */
public class AaIllegalTransitionException extends RuntimeException {

    /** Translation key. Never an English literal — the message below is for logs only. */
    public static final String MESSAGE_KEY = "aa.workflow.error_illegal_transition";

    private final String action;
    private final String currentStatus;
    private final List<String> availableActions;

    public AaIllegalTransitionException(String action, String currentStatus,
                                        List<String> availableActions) {
        super("Action " + action + " is not allowed from status '" + currentStatus + "'");
        this.action = action;
        this.currentStatus = currentStatus;
        this.availableActions = availableActions == null ? List.of() : availableActions;
    }

    public String getAction() {
        return action;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public List<String> getAvailableActions() {
        return availableActions;
    }

    public String getMessageKey() {
        return MESSAGE_KEY;
    }
}
