package com.rbi.cms.mailintake.entity;

/**
 * RECEIVED -> PARSED -> NORMALISED -> DISPATCHED -> PROCESSED, with every stage able to fall to
 * FAILED (retryable) or QUARANTINED (terminal until an operator replays it). See
 * {@link com.rbi.cms.mailintake.state.InboundEmailStateMachine} for the legal-transition table —
 * this enum intentionally carries no transition logic itself.
 */
public enum InboundEmailStatus {
    RECEIVED,
    PARSED,
    NORMALISED,
    DISPATCHED,
    PROCESSED,
    FAILED,
    QUARANTINED
}
