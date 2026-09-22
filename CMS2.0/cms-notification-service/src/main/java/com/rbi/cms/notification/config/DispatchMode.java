package com.rbi.cms.notification.config;

/**
 * Whether this service really sends, set by {@code cms.notification.mode}.
 */
public enum DispatchMode {

    /**
     * Record the outcome without contacting any mail server or gateway: the row is marked SENT and
     * nothing leaves the process. The default, so an unconfigured deployment cannot mail anybody.
     */
    SIMULATE,

    /** Actually dispatch, through the configured SMTP server. Requires the channel to be enabled too. */
    SEND
}
