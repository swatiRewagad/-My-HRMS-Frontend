package com.hrms.cms.entity;

/**
 * Status vocabulary for {@link NodalOfficerRecord}.
 *
 * <p>These values were previously inline string literals spread across the entity's field initialiser
 * and {@code NotificationScheduledTasks}, which meant the staleness sweep matched on a literal that
 * nothing tied to the value the entity actually writes. A typo in either place would silently stop the
 * 15/20-day escalations from ever firing — the sweep would simply return zero rows and log success.
 * Centralising the vocabulary makes that class of failure a compile error instead.
 */
public final class NodalOfficerRecordStatus {

    /**
     * UST570 / UST574: a record starts life unconfirmed. It means "we have a row, we do not yet have
     * the entity's word that these contacts are current" — which is why it is also the status the
     * staleness sweep escalates on.
     */
    public static final String INFORMATION_REQUIRED = "INFORMATION_REQUIRED";

    /** The entity (or a Dealing Officer on their behalf) has confirmed the contacts are current. */
    public static final String CONFIRMED = "CONFIRMED";

    /** Contacts are known to be wrong and a replacement has been requested from the entity. */
    public static final String UPDATE_REQUESTED = "UPDATE_REQUESTED";

    private NodalOfficerRecordStatus() {
    }
}
