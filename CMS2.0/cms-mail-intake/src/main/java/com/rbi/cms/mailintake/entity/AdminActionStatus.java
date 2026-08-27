package com.rbi.cms.mailintake.entity;

/** Maker-checker lifecycle for {@link AdminAction} — no in-between states, no edits after
 *  decision; a mis-decided action gets a fresh request rather than a mutation of the old one. */
public enum AdminActionStatus {
    PENDING,
    APPROVED,
    REJECTED
}
