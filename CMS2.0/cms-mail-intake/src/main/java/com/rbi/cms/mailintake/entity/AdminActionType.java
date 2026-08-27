package com.rbi.cms.mailintake.entity;

/** What an operator asked the maker-checker workflow to do — see {@link AdminAction}. */
public enum AdminActionType {
    /** Send a QUARANTINED or FAILED email back through the pipeline from RECEIVED. */
    REPLAY,
    /** Manually set linked_complaint_id/complaint_ref on an email the resolver chain (or a
     *  human) has independently confirmed belongs to a specific complaint. Does not itself
     *  change the email's status — see AdminMailIntakeService. */
    FORCE_LINK
}
