package com.hrms.cms.service;

import com.hrms.cms.entity.AppealStatus;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * THE transition table for an AA appeal. One declaration, consulted by both the enforcement path
 * ({@code performAction}) and the advertisement path ({@code getAvailableActions}).
 *
 * Why this type exists: those two paths were separate. The role matrix and the status rules lived in
 * different places, {@code performAction} validated only the role, and the terminal-state check existed
 * ONLY in the read-only helper. The result was demonstrable on a live server — a freshly filed appeal
 * was driven straight to {@code order_passed}, skipping acceptance, review and hearing; a passed order
 * was then overwritten by a second PASS_ORDER; and a terminal appeal was pulled back to
 * {@code under_review} by ACCEPT. Meanwhile the UI offered actions the server would refuse and refused
 * actions the server would allow. A single table makes those disagreements unrepresentable.
 *
 * Each constant declares: who may act, from which statuses, and what the resulting status is. Ordering
 * follows the lifecycle so the table reads as the process: filed -> DO -> reviewer -> secretariat ->
 * order.
 */
public enum AaWorkflowTransition {

    // ── AA_DO: intake and routing ───────────────────────────────────────────────────────────────
    ACCEPT("AA_DO",
            from(AppealStatus.FILED),
            AppealStatus.UNDER_REVIEW,
            AaWorkflowEvent.ACCEPTED),

    REJECT("AA_DO",
            from(AppealStatus.FILED),
            AppealStatus.REJECTED,
            AaWorkflowEvent.REJECTED),

    /** Hands the file to a reviewer. Status is already under_review; only the holder changes. */
    ASSIGN_TO_BENCH("AA_DO",
            from(AppealStatus.UNDER_REVIEW),
            null,
            AaWorkflowEvent.ASSIGNED_TO_REVIEWER),

    REQUEST_DOCUMENTS("AA_DO",
            from(AppealStatus.FILED, AppealStatus.UNDER_REVIEW),
            null,
            AaWorkflowEvent.DOCUMENTS_REQUESTED),

    // ── AA_REVIEWER: assessment ─────────────────────────────────────────────────────────────────
    PREPARE_BRIEF("AA_REVIEWER",
            from(AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            null,
            null),

    /**
     * Tier-1 reviewer escalates to tier 2.
     *
     * Escalation-only by ruling: tier 2 is not a mandatory second review, because a compulsory extra
     * stage would double handling time for every appeal and no Scheme text was available to require it.
     */
    ESCALATE_TO_TIER2("AA_REVIEWER",
            from(AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            null,
            AaWorkflowEvent.ESCALATED_TO_TIER2),

    FORWARD_TO_AUTHORITY("AA_REVIEWER",
            from(AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            null,
            AaWorkflowEvent.FORWARDED_TO_AUTHORITY),

    /** Back to the DO who routed it — not to a random DO. */
    SEND_BACK_REGISTRAR("AA_REVIEWER",
            from(AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            null,
            AaWorkflowEvent.SENT_BACK),

    // ── Hearings: either the reviewer or the secretariat may fix one ────────────────────────────
    SCHEDULE_HEARING(Set.of("AA_REVIEWER", "AA_SECRETARIAT"),
            from(AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            AppealStatus.HEARING_SCHEDULED,
            AaWorkflowEvent.HEARING_SCHEDULED),

    // ── AA_SECRETARIAT: disposal ────────────────────────────────────────────────────────────────
    PASS_ORDER("AA_SECRETARIAT",
            from(AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            AppealStatus.ORDER_PASSED,
            AaWorkflowEvent.ORDER_PASSED),

    REMAND_TO_OMBUDSMAN("AA_SECRETARIAT",
            from(AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            AppealStatus.CLOSED,
            AaWorkflowEvent.REMANDED),

    DISMISS("AA_SECRETARIAT",
            from(AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            AppealStatus.CLOSED,
            AaWorkflowEvent.DISMISSED),

    // ── AA_ADMIN: override. Deliberately the ONLY way out of a terminal state. ──────────────────
    REASSIGN("AA_ADMIN",
            from(AppealStatus.FILED, AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            null,
            AaWorkflowEvent.REASSIGNED),

    CLOSE("AA_ADMIN",
            from(AppealStatus.FILED, AppealStatus.UNDER_REVIEW, AppealStatus.HEARING_SCHEDULED),
            AppealStatus.CLOSED,
            AaWorkflowEvent.CLOSED),

    /**
     * The single legitimate escape from a terminal state, restricted to AA_ADMIN and audited with a
     * mandatory reason. Everything else refuses to act on a disposed appeal.
     */
    REOPEN("AA_ADMIN",
            from(AppealStatus.ORDER_PASSED, AppealStatus.CLOSED, AppealStatus.REJECTED),
            AppealStatus.UNDER_REVIEW,
            AaWorkflowEvent.REOPENED);

    private final Set<String> allowedRoles;
    private final Set<String> fromStatuses;
    private final AppealStatus toStatus;
    private final AaWorkflowEvent event;

    AaWorkflowTransition(String allowedRole, Set<String> fromStatuses, AppealStatus toStatus,
                         AaWorkflowEvent event) {
        this(Set.of(allowedRole), fromStatuses, toStatus, event);
    }

    AaWorkflowTransition(Set<String> allowedRoles, Set<String> fromStatuses, AppealStatus toStatus,
                         AaWorkflowEvent event) {
        this.allowedRoles = allowedRoles;
        this.fromStatuses = fromStatuses;
        this.toStatus = toStatus;
        this.event = event;
    }

    private static Set<String> from(AppealStatus... statuses) {
        Set<String> codes = new LinkedHashSet<>();
        for (AppealStatus status : statuses) {
            codes.add(status.getCode());
        }
        return codes;
    }

    public Set<String> getAllowedRoles() {
        return allowedRoles;
    }

    public Set<String> getFromStatuses() {
        return fromStatuses;
    }

    /** Null when the action changes ownership or metadata but not the status. */
    public AppealStatus getToStatus() {
        return toStatus;
    }

    /** Null when the action is internal and nobody needs telling (e.g. PREPARE_BRIEF). */
    public AaWorkflowEvent getEvent() {
        return event;
    }

    public boolean permits(String role) {
        return role != null && allowedRoles.contains(role);
    }

    public boolean allowedFrom(String status) {
        return status != null && fromStatuses.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    /** Case-insensitive lookup. Empty for an unknown action — callers must reject, never default. */
    public static Optional<AaWorkflowTransition> find(String action) {
        if (action == null || action.isBlank()) {
            return Optional.empty();
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.name().equals(normalized)).findFirst();
    }

    /**
     * Everything {@code role} may do to an appeal currently in {@code status}.
     *
     * This is the ONLY derivation of available actions. The UI renders exactly this, so it can never
     * offer an action the server would refuse, nor hide one it would allow.
     */
    public static List<String> availableFor(String role, String status) {
        return Arrays.stream(values())
                .filter(t -> t.permits(role))
                .filter(t -> t.allowedFrom(status))
                .map(Enum::name)
                .toList();
    }
}
