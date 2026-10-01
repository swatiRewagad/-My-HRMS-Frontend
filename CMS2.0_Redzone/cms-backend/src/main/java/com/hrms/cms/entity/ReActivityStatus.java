package com.hrms.cms.entity;

import java.util.Arrays;
import java.util.List;

/**
 * The 7-level RE Activity Status ladder (UST846).
 *
 * This is deliberately NOT the same concept as {@code Complaint.status}. Complaint.status is the
 * regulatory workflow state ("pending", "re_responded", "closed"); this is a progress signal that
 * tells CEPC/RBI staff whether the entity has actually started working on the record. A complaint
 * can sit at Complaint.status="pending" for a fortnight while the RE moves NOT_OPENED → OPENED →
 * UNDER_REVIEW, and that difference is the whole point of the story: staff currently have to
 * telephone the entity to find out.
 *
 * Values are UPPER_SNAKE_CASE, matching {@code ComplaintQuery} and the other newer tables rather
 * than the legacy lowercase Complaint.status convention. Storing a mixed-case vocabulary is what
 * produced the dead CSS this batch has to unpick, so new columns do not repeat it.
 *
 * {@link #rank()} exists because the ladder is monotonic for the RE-driven levels: an entity that
 * has uploaded documents cannot go back to merely having opened the record, so a late-arriving
 * event must not regress the badge. OVERDUE is outside that ordering — see {@link #isTerminal()}.
 */
public enum ReActivityStatus {

    /** Forwarded to the entity; nobody there has opened it yet. The implicit initial state. */
    NOT_OPENED(0, "re.activity.not_opened", true),

    /** An RE user fetched the record detail at least once. */
    OPENED(1, "re.activity.opened", true),

    /** An RE user saved a draft with no response text yet — looking, not yet writing. */
    UNDER_REVIEW(2, "re.activity.under_review", true),

    /** An RE user saved a draft containing response text. */
    RESPONSE_BEING_PREPARED(3, "re.activity.response_being_prepared", true),

    /** At least one supporting document has been attached. */
    DOCUMENTS_UPLOADED(4, "re.activity.documents_uploaded", false),

    /** The entity submitted its formal response. End of the RE-driven ladder. */
    RESPONSE_SUBMITTED(5, "re.activity.response_submitted", false),

    /**
     * The response window expired with no submission. Set only by the scheduled sweep
     * (UST849), never by an RE action, and never for a record that already reached
     * RESPONSE_SUBMITTED.
     */
    OVERDUE(6, "re.activity.overdue", false);

    private final int rank;
    private final String translationKey;
    private final boolean nudgeable;

    ReActivityStatus(int rank, String translationKey, boolean nudgeable) {
        this.rank = rank;
        this.translationKey = translationKey;
        this.nudgeable = nudgeable;
    }

    public int rank() {
        return rank;
    }

    public String translationKey() {
        return translationKey;
    }

    /**
     * Whether a record stuck in this status should attract a nudge (UST850). Only the early
     * levels qualify: once documents are up or a response is in, silence is not a problem, and
     * nudging an already-OVERDUE record would duplicate the SLA escalation the story explicitly
     * says nudges must not replace.
     */
    public boolean isNudgeable() {
        return nudgeable;
    }

    /** RESPONSE_SUBMITTED and OVERDUE end the ladder; nothing further is derived from RE actions. */
    public boolean isTerminal() {
        return this == RESPONSE_SUBMITTED || this == OVERDUE;
    }

    /**
     * Forward-only guard. Returns true when {@code target} is a legitimate advance from this
     * status. OVERDUE is reachable from any non-terminal status regardless of rank, because the
     * clock expiring is not an RE action and must not be blocked by ladder position.
     */
    public boolean canAdvanceTo(ReActivityStatus target) {
        if (target == null || target == this) {
            return false;
        }
        if (target == OVERDUE) {
            return this != RESPONSE_SUBMITTED && this != OVERDUE;
        }
        if (this == OVERDUE) {
            // A late response still counts: the entity is allowed to climb out of OVERDUE.
            return target == RESPONSE_SUBMITTED || target == DOCUMENTS_UPLOADED;
        }
        return target.rank > this.rank;
    }

    public static ReActivityStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return NOT_OPENED;
        }
        for (ReActivityStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown RE activity status: " + code);
    }

    /** The statuses a nudge sweep needs to consider. */
    public static List<ReActivityStatus> nudgeableStatuses() {
        return Arrays.stream(values()).filter(ReActivityStatus::isNudgeable).toList();
    }
}
