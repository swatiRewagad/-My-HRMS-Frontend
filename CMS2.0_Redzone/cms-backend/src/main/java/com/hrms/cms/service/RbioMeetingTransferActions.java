package com.hrms.cms.service;

import com.hrms.cms.entity.RbioWorkflowTransition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * S5's meeting and forwarding actions (UST496-503, 643-651, 556-568, 632-634, 759-761, 765-766, 770-772).
 *
 * <p><b>Why a third declaration class rather than edits to the first two.</b> {@link RbioTransitionRegistry}
 * is Wave 0's and its role/action matrix is asserted cell by cell — including the NEGATIVE cells — to catch
 * privilege creep; {@link RbioLadderActions} is S3's. Editing either would change another session's file and
 * the tests that exist to detect exactly that. This class is additive in the same way S3's is: the service
 * resolves from the DATABASE first, and these rows are seeded into it by {@code RbioMeetingTransferSeeder}.
 *
 * <p><b>What was broken.</b>
 *
 * <ol>
 *   <li>Wave 0's {@code SCHEDULE_MEETING} was the only meeting action. There was no RESCHEDULE and no
 *       COMPLETE, so UST502's "retain the previous meeting details" and UST499's mandatory minutes had no
 *       action to hang off at all.</li>
 *   <li>{@code SCHEDULE_MEETING} declared NO required params, so a meeting could be recorded with no date,
 *       no time and no participants — the browser's disabled button was the only control.</li>
 *   <li>{@code RBIO_REVIEWER} was never granted {@code SCHEDULE_MEETING}: the Reviewer mirrors the
 *       Supervisor's action set, which omits it. UST643 requires the milestone to be accessible to the
 *       Reviewer, so that grant is added here.</li>
 *   <li>{@code FORWARD_TO_OTHER_OFFICE} and {@code FORWARD_TO_OTHER_RBI_DEPT} are dispatched by
 *       {@code task-action.component.ts} on the RBIO route but existed only in {@code CepcWorkflowService},
 *       so on RBIO they hit "Unknown RBIO action" and threw.</li>
 * </ol>
 *
 * <p><b>The status exclusions are NOT expressed as from-statuses here.</b> UST497 hides the meeting option
 * for six named statuses, which is a NOT-IN. Enumerating "every status except six" as positive rows would
 * silently omit any status a later session adds — the trap {@link RbioTransitionRegistry} documents for
 * REASSIGN. The exclusion lives on {@code RBIO_STATUS_MASTER.BLOCKS_MEETING} and is enforced by
 * {@code RbioMeetingService.assertMeetingAllowedForStatus}, which runs on EXECUTION and not merely on
 * advertisement.
 *
 * <p><b>No clause citation appears in this file.</b> A forward that closes a complaint closes it under a
 * clause supplied by the caller and validated against the closure-clause master. Compiling a clause number
 * in here would put a legal citation in a Java constant, which is exactly the defect the Scheme-year drift
 * bug came from.
 */
public final class RbioMeetingTransferActions {

    /** Owner tag on every row this class seeds, so its rows are identifiable in the table. */
    private static final String OWNER = "S5";

    // ── Meetings (UST496-503, 643-651) ──
    /**
     * Wave 0 already declares {@code SCHEDULE_MEETING}. It is RE-declared here for two reasons: to attach
     * the mandatory date/time/participants that Wave 0's row omits, and to grant it to
     * {@code RBIO_REVIEWER}, whom UST643 requires to reach the milestone. Because
     * {@code RbioWorkflowService} resolves the DATABASE first and this class's rows are seeded, the
     * stricter declaration wins at runtime while Wave 0's pinned unit tests continue to see their own.
     */
    public static final String SCHEDULE_MEETING   = "SCHEDULE_MEETING";
    public static final String RESCHEDULE_MEETING = "RESCHEDULE_MEETING";
    public static final String COMPLETE_MEETING   = "COMPLETE_MEETING";

    // ── Forwarding (UST556-568, 759-761, 765-766, 770-772) ──
    public static final String FORWARD_TO_OTHER_OFFICE   = "FORWARD_TO_OTHER_OFFICE";
    public static final String FORWARD_TO_OTHER_RBI_DEPT = "FORWARD_TO_OTHER_RBI_DEPT";

    // ── Side effects this class introduces, applied by RbioWorkflowService ──
    public static final String FX_MEETING_SCHEDULED   = "MEETING_SCHEDULED";
    public static final String FX_MEETING_RESCHEDULED = "MEETING_RESCHEDULED";
    public static final String FX_MEETING_COMPLETED   = "MEETING_COMPLETED";
    public static final String FX_TRANSFER_REQUEST    = "TRANSFER_REQUEST";
    public static final String FX_FORWARD_DEPARTMENT  = "FORWARD_DEPARTMENT";

    /** The four roles UST643/646/649 require to reach the meeting milestone, plus the legacy equivalents. */
    private static final String[] MEETING_ROLES = {
            RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN,
            RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR};

    /**
     * From-statuses a meeting may be convened from.
     *
     * <p>A POSITIVE list is still needed for ADVERTISEMENT — it is what the UI offers — but it is not what
     * ENFORCES the exclusion. The two must not be confused: this list omits the six excluded statuses
     * naturally, while {@code BLOCKS_MEETING} is what refuses a direct POST.
     */
    private static final String[] MEETING_FROM = {
            "in_progress", "assigned", "conciliation", "escalated", "returned",
            "reviewer_review", "deputy_review", "ombudsman_review"};

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        // ═══════════════ Meetings ═══════════════
        // Status deliberately UNCHANGED (null toStatus) on all three, which is what satisfies UST503: a
        // reschedule must not move the complaint's overall workflow status. The MILESTONE moves to
        // CONCILIATION so the file shows where it is, and the STAGE records which meeting event last
        // occurred.
        //
        // requiredParams carries the mandatory fields. They are ALSO validated in RbioMeetingService with
        // specific, translatable messages — the table-level requirement is the backstop that applies even
        // to a caller that reaches the transition by another route.
        // Only the DATE is declared required at the table level. Time and participants are equally mandatory
        // (UST496) but are enforced in RbioMeetingService, which can honour a caller that expressed them
        // differently — a full ISO timestamp already states the time, and the pre-existing screen named the
        // parties in free text. The table cannot express "required unless derivable", and declaring them here
        // refused a caller that was working, which is a backward-compatibility break rather than a control.
        //
        // Nothing is weakened by this: the service refuses an absent time or participants it cannot derive,
        // and it is reached by every path that reaches the transition.
        SPECS.put(SCHEDULE_MEETING, Spec.of(null, "MEETING_SCHEDULED", "CONCILIATION")
                .require("meetingDate|hearingDate")
                .fx(FX_MEETING_SCHEDULED)
                .from(MEETING_FROM)
                .roles(MEETING_ROLES));

        SPECS.put(RESCHEDULE_MEETING, Spec.of(null, "MEETING_RESCHEDULED", "CONCILIATION")
                .require("meetingDate|hearingDate")
                // UST502: save is blocked until the Reason is completed. Declared here as well because a
                // reschedule has no legacy caller to stay compatible with — it is a new action.
                .require("rescheduleReason|reason")
                .fx(FX_MEETING_RESCHEDULED)
                .from(MEETING_FROM)
                .roles(MEETING_ROLES));

        // UST499: Entity acceptance (Yes/No) and a free-text MOM are both mandatory.
        SPECS.put(COMPLETE_MEETING, Spec.of(null, "MEETING_COMPLETED", "CONCILIATION")
                .require("entityAccepted|entityAcceptance")
                .require("minutesOfMeeting|mom")
                .fx(FX_MEETING_COMPLETED)
                .from(MEETING_FROM)
                .roles(MEETING_ROLES));

        // ═══════════════ Forwarding to another office (UST556-568, 770) ═══════════════
        // UST557/560: ownership transfers to the CRPC operational head on Save and Proceed and the status
        // becomes "Sent to Other Office" immediately. sent_to_other is the LEGACY_VALUE the status master
        // already declares for SENT_TO_OTHER_OFFICE — CEPC's existing arm writes forwarded_external here,
        // which is why transferred complaints never appeared in the Head's queue.
        //
        // NO assign strategy: the destination officer is NOT chosen now. The complaint waits for the CRPC
        // Head's approval, and only then is a real Dealing Officer resolved (UST564: the transfer enters the
        // approval queue BEFORE reaching the destination DO). Assigning here would hand the file to the
        // destination before anyone approved the move.
        SPECS.put(FORWARD_TO_OTHER_OFFICE, Spec.of("sent_to_other", "SENT_TO_OTHER_OFFICE", "FORWARD")
                // UST559/556: Reason for Transfer and the target office are both mandatory before Save and
                // Proceed. targetOffice carries the destination OFFICE_CODE; transferOffice names whether it
                // is an RBIO or a CEPC destination.
                .require("targetOffice|toOffice")
                .require("transferReason|reason")
                .comment()
                .fx(FX_TRANSFER_REQUEST)
                .from("in_progress", "assigned", "escalated", "returned",
                        "reviewer_review", "deputy_review", "ombudsman_review", "conciliation")
                // UST765/525: build once, authorise twice — the Deputy Ombudsman's version of the action is
                // the same action, and the CRPC Head approval flow applies identically.
                .roles(RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
                        RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR));

        // ═══════════════ Forwarding to an RBI department (UST761/534/527-528) ═══════════════
        // forwarded_external is SENT_TO_OTHER_DEPT's legacy value. The department is recorded in its own
        // column rather than written into assignedOfficer, which is the defect the CEPC arms have.
        SPECS.put(FORWARD_TO_OTHER_RBI_DEPT, Spec.of("forwarded_external", "SENT_TO_OTHER_DEPT", "FORWARD")
                .require("targetDepartment|targetDept")
                .comment()
                .fx(FX_FORWARD_DEPARTMENT)
                .from("in_progress", "assigned", "escalated", "returned",
                        "reviewer_review", "deputy_review", "ombudsman_review")
                .roles(RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
                        RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR));
    }

    /** Every action this class declares. */
    public static Set<String> allActions() {
        return new LinkedHashSet<>(SPECS.keySet());
    }

    /** Actions the role may perform, from these declarations alone. */
    public static Set<String> actionsFor(String role) {
        if (role == null) return Set.of();
        String upper = role.toUpperCase(Locale.ROOT);
        Set<String> actions = new LinkedHashSet<>();
        SPECS.forEach((action, spec) -> {
            if (spec.roles.contains(upper)) {
                actions.add(action);
            }
        });
        return actions;
    }

    /** Whether the action should be OFFERED for a complaint currently in {@code status}. */
    public static boolean validForState(String action, String status) {
        if (action == null || status == null) return false;
        Spec spec = SPECS.get(action.toUpperCase(Locale.ROOT));
        if (spec == null) return false;
        return spec.fromStatuses.isEmpty() || spec.fromStatuses.contains(status);
    }

    /** The transition for an action performed by a role, or null when that role may not perform it. */
    public static RbioWorkflowTransition resolve(String action, String role) {
        if (action == null || role == null) return null;
        String upperAction = action.toUpperCase(Locale.ROOT);
        Spec spec = SPECS.get(upperAction);
        if (spec == null || !spec.roles.contains(role.toUpperCase(Locale.ROOT))) return null;
        return spec.toRow(upperAction, role.toUpperCase(Locale.ROOT), null);
    }

    /** The effect of an action irrespective of role, for the role-less execution path. */
    public static RbioWorkflowTransition effectOf(String action) {
        if (action == null) return null;
        String upperAction = action.toUpperCase(Locale.ROOT);
        Spec spec = SPECS.get(upperAction);
        return spec == null ? null : spec.toRow(upperAction, "", null);
    }

    /** Every row to seed: one per (action, role, from-status). */
    public static List<RbioWorkflowTransition> allRows() {
        List<RbioWorkflowTransition> rows = new ArrayList<>();
        SPECS.forEach((action, spec) -> {
            for (String role : spec.roles) {
                if (spec.fromStatuses.isEmpty()) {
                    rows.add(spec.toRow(action, role, null));
                } else {
                    for (String from : spec.fromStatuses) {
                        rows.add(spec.toRow(action, role, from));
                    }
                }
            }
        });
        return rows;
    }

    /** A declarative action effect, expanded into one row per (role, from-status). */
    private static final class Spec {
        private final String toStatus;
        private final String toStage;
        private final String toMilestone;
        private final Set<String> roles = new LinkedHashSet<>();
        private final Set<String> fromStatuses = new LinkedHashSet<>();
        private String assignToRole;
        private String assignStrategy;
        private String slaStage;
        private String closureCause;
        private String requiredParams;
        private String sideEffect;
        private boolean terminal;
        private boolean requiresComment;

        private Spec(String toStatus, String toStage, String toMilestone) {
            this.toStatus = toStatus;
            this.toStage = toStage;
            this.toMilestone = toMilestone;
        }

        static Spec of(String toStatus, String toStage, String toMilestone) {
            return new Spec(toStatus, toStage, toMilestone);
        }

        Spec assign(String role, String strategy) {
            this.assignToRole = role;
            this.assignStrategy = strategy;
            return this;
        }

        Spec sla(String stage) {
            this.slaStage = stage;
            return this;
        }

        Spec closure(String cause) {
            this.closureCause = cause;
            return this;
        }

        /** Appended, so several require(..) calls accumulate as separate mandatory groups. */
        Spec require(String params) {
            this.requiredParams = (requiredParams == null || requiredParams.isBlank())
                    ? params : requiredParams + "," + params;
            return this;
        }

        Spec fx(String name) {
            this.sideEffect = (sideEffect == null || sideEffect.isBlank()) ? name : sideEffect + "," + name;
            return this;
        }

        Spec terminal() {
            this.terminal = true;
            return this;
        }

        Spec comment() {
            this.requiresComment = true;
            return this;
        }

        Spec from(String... statuses) {
            this.fromStatuses.addAll(List.of(statuses));
            return this;
        }

        Spec roles(String... roleNames) {
            this.roles.addAll(List.of(roleNames));
            return this;
        }

        RbioWorkflowTransition toRow(String action, String role, String fromStatus) {
            return RbioWorkflowTransition.builder()
                    .actionCode(action)
                    .roleName(role)
                    .fromStatus(fromStatus)
                    .toStatus(toStatus)
                    .toStage(toStage)
                    .toMilestone(toMilestone)
                    .assignToRole(assignToRole)
                    .assignStrategy(assignStrategy)
                    .slaStage(slaStage)
                    .closureCause(closureCause)
                    .requiredParams(requiredParams)
                    .sideEffect(sideEffect)
                    .isTerminal(terminal ? "Y" : "N")
                    .requiresComment(requiresComment ? "Y" : "N")
                    .displayOrder(999)
                    .isActive("Y")
                    .schemeVersion("RBIOS_2021")
                    .ownedBy(OWNER)
                    .build();
        }
    }

    private RbioMeetingTransferActions() {
    }
}
