package com.hrms.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of an assignment attempt.
 *
 * Never null and never a bare user id string: callers need to know WHY an officer was chosen, because
 * a threshold breach and a vernacular override are both legitimate outcomes that must be visible
 * rather than silently folded into "assigned". The predecessor round-robin service returned a bare
 * String and logged breaches at warn level, so a breach was indistinguishable from a normal placement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AaAssignmentResult {

    /** How the officer was selected, or why nobody was. */
    public enum Outcome {
        /** Ordinary round-robin placement within threshold. */
        ASSIGNED,
        /** Routed to a language-skilled officer, bypassing round robin. Not charged to threshold. */
        VERNACULAR_OVERRIDE,
        /**
         * Placed above threshold under an explicitly configured grace allowance, after the rotation
         * pointer was reset because every officer had reached their limit. Audited as a breach.
         */
        ASSIGNED_UNDER_GRACE,
        /** AA Admin placed this by hand, bypassing threshold. Audited with a reason. */
        MANUAL_OVERRIDE,
        /** Nobody eligible and no grace available. Nothing was assigned. */
        UNASSIGNED_POOL_EXHAUSTED,
        /** No officer is configured in this pool at all. Nothing was assigned. */
        UNASSIGNED_POOL_EMPTY
    }

    private Outcome outcome;

    /** Chosen officer's user id, or null on either UNASSIGNED_* outcome. */
    private String assignedUserId;

    private String roleGroup;

    /** The officer's assigned-draft count AFTER this placement. */
    private Integer workloadAfter;

    /** The officer's configured threshold at decision time. 0 means unlimited. */
    private Integer thresholdAtAssignment;

    /** True when this placement was not charged against the threshold (vernacular override). */
    private boolean thresholdExempt;

    /** Translation key describing the outcome. Never an English literal. */
    private String messageKey;

    public boolean isAssigned() {
        return assignedUserId != null;
    }
}
