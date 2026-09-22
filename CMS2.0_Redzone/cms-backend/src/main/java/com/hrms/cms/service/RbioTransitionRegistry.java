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
 * The RBIO transition declarations: who may do what, from where, and what results.
 *
 * <p>THE single source of truth, consumed by three collaborators that used to disagree:
 * {@code RbioWorkflowTransitionSeeder} writes these rows to RBIO_WORKFLOW_TRANSITION,
 * {@code RbioWorkflowService} enforces them, and the available-actions endpoint advertises them.
 *
 * <p><b>Why a plain class and not the database alone.</b> {@code RbioWorkflowService} resolves from the
 * DB table FIRST, so sessions S1-S7 extend the workflow by INSERTING ROWS. This class is the fallback
 * when the table has no matching row. That fallback is not defensive padding — it is required, because
 * there is no Flyway in this tree: {@code database/*.sql} is hand-run, so a database on which V58 was
 * never applied is a normal occurrence. Without the fallback such a database would have an empty
 * transition table and every RBIO action would be refused, where the switch this replaces always
 * worked. It also keeps {@code RbioWorkflowServiceTest} meaningful without a database.
 *
 * <p><b>Three properties of the switches being replaced, preserved deliberately.</b>
 *
 * <ol>
 *   <li>{@code executeAction} took NO role, so an action's EFFECT is role-independent: ESCALATE writes
 *       the same columns whoever performs it. An effect is therefore declared ONCE in {@link #EFFECTS}
 *       and the per-role rows are generated from it. Declaring the effect per (action, role) row would
 *       permit a drift between two rows for one action that the old switch could not express.</li>
 *   <li>{@code performAction} never called {@code isActionValidForState} — from-status governed only
 *       what was ADVERTISED. So {@link #resolve} does not reject on from-status, while
 *       {@link #validForState} does. Enforcing it on execution would start refusing writes that
 *       succeed today, which is a behaviour change, not a refactor.</li>
 *   <li>Role authorisation was a flat role→actions map, independent of status.</li>
 * </ol>
 */
public final class RbioTransitionRegistry {

    /** Assignment strategies. */
    public static final String ROUND_ROBIN  = "ROUND_ROBIN";
    public static final String TARGET_PARAM = "TARGET_PARAM";
    public static final String ACTOR        = "ACTOR";

    /**
     * Send the file back to the officer who previously held the target role.
     *
     * <p>Distinct from TARGET_PARAM because the officer is DERIVED from custody history rather than named
     * by the caller, and distinct from ROUND_ROBIN because a send-back that lands on an arbitrary holder
     * of the role is not a send-back. When no active previous holder exists the action is REFUSED so the
     * caller must choose explicitly — see {@code RbioCaseAssignmentHistoryService}.
     *
     * <p>An explicit {@code targetUser} still wins, which is what makes the forced-manual-selection
     * pop-up work: the client retries the same action naming the officer the user picked.
     */
    public static final String PREVIOUS_HOLDER = "PREVIOUS_HOLDER";

    /** "One step up the ladder", resolved at runtime via {@link RbioRoles#nextRank}. */
    public static final String NEXT_RANK = "__NEXT__";

    /** "One step down the ladder", resolved at runtime via {@link RbioRoles#previousRank}. */
    public static final String PREVIOUS_RANK = "__PREVIOUS__";

    // ── Side-effect names. Effects a table cannot express; applied by RbioWorkflowService. ──
    public static final String FX_RESOLVED_AT         = "RESOLVED_AT";
    public static final String FX_CLOSED_AT           = "CLOSED_AT";
    public static final String FX_CLOSURE_FROM_PARAM  = "CLOSURE_FROM_PARAM";
    public static final String FX_STAMP_ESCALATED     = "STAMP_ESCALATED";
    public static final String FX_APPROVE_LADDER      = "APPROVE_LADDER";
    public static final String FX_MEETING_DATE        = "MEETING_DATE";
    public static final String FX_ADVISORY_TEXT       = "ADVISORY_TEXT";
    public static final String FX_AWARD               = "AWARD";
    public static final String FX_ADJUDICATION_REJECT = "ADJUDICATION_REJECT";
    public static final String FX_NOTICE_13_1         = "NOTICE_13_1";
    public static final String FX_IMPLEAD             = "IMPLEAD";
    public static final String FX_REASSIGN_ROLE       = "REASSIGN_ROLE";
    public static final String FX_BULK_ROLE           = "BULK_ROLE";
    public static final String FX_REOPEN              = "REOPEN";
    public static final String FX_CONCILIATION_SUCCESS = "CONCILIATION_SUCCESS_FLAG";
    public static final String FX_CONCILIATION_FAILED  = "CONCILIATION_FAILED_FLAG";

    /**
     * The effect of each action — exactly the column writes the corresponding {@code executeAction} arm
     * performed. Every literal here is pinned by {@code RbioWorkflowServiceTest}.
     */
    private static final Map<String, Effect> EFFECTS = new LinkedHashMap<>();

    static {
        // ACCEPT and TAKE_ACTION shared one arm by fall-through, so they share one effect.
        // ACTOR strategy = the performer takes ownership, i.e. setAssignedOfficer(actor).
        EFFECTS.put("ACCEPT",      Effect.of("in_progress", "EXAMINATION", "ASSESSMENT").assign(null, ACTOR).sla("OFFICER_ASSESSMENT"));
        EFFECTS.put("TAKE_ACTION", Effect.of("in_progress", "EXAMINATION", "ASSESSMENT").assign(null, ACTOR).sla("OFFICER_ASSESSMENT"));

        // APPROVE's resulting STATUS depends on which role it lands on, so it cannot be a literal here:
        // landing on a conciliator yields "conciliation", on an adjudicator "adjudication", otherwise
        // "escalated". Declaring any one of those would break the pinned behaviour of the other two.
        EFFECTS.put("APPROVE", Effect.of(null, null, null).assign(NEXT_RANK, ROUND_ROBIN).fx(FX_APPROVE_LADDER));

        EFFECTS.put("REJECT", Effect.of("rejected", "CLOSED", "FINAL_DECISION")
                .closure("REJECTED").fx(FX_RESOLVED_AT).terminal());

        EFFECTS.put("ESCALATE", Effect.of("escalated", "ESCALATED", "ASSESSMENT")
                .assign(NEXT_RANK, ROUND_ROBIN).fx(FX_STAMP_ESCALATED));

        EFFECTS.put("RETURN_TO_OFFICER", Effect.of("returned", "RETURNED_TO_OFFICER", "ASSESSMENT")
                .assign(RbioRoles.OFFICER, TARGET_PARAM));

        EFFECTS.put("RESOLVE", Effect.of("resolved", "RESOLVED", "FINAL_DECISION")
                .closure("RESOLVED").fx(FX_CLOSURE_FROM_PARAM).fx(FX_RESOLVED_AT));

        EFFECTS.put("REQUEST_INFO", Effect.of("info_requested", "AWAITING_INFO", "ASSESSMENT"));

        // Status deliberately UNCHANGED — this arm set only the stage and the meeting date.
        EFFECTS.put("SCHEDULE_MEETING", Effect.of(null, "MEETING_SCHEDULED", "CONCILIATION").fx(FX_MEETING_DATE));

        EFFECTS.put("ISSUE_ADVISORY", Effect.of("advisory_issued", "ADVISORY_ISSUED", "FINAL_DECISION")
                .closure("ADVISORY").fx(FX_ADVISORY_TEXT));

        EFFECTS.put("FORWARD_TO_CONCILIATION", Effect.of("conciliation", "CONCILIATION", "CONCILIATION")
                .assign(RbioRoles.CONCILIATOR, ROUND_ROBIN).sla("CONCILIATION"));

        EFFECTS.put("FORWARD_TO_ADJUDICATION", Effect.of("adjudication", "ADJUDICATION", "FINAL_DECISION")
                .assign(RbioRoles.ADJUDICATOR, ROUND_ROBIN).sla("ADJUDICATION"));

        EFFECTS.put("ESCALATE_TO_ADJUDICATION", Effect.of("adjudication", "ADJUDICATION", "FINAL_DECISION")
                .assign(RbioRoles.ADJUDICATOR, ROUND_ROBIN).sla("ADJUDICATION").fx(FX_CONCILIATION_FAILED));

        EFFECTS.put("CONCILIATION_SUCCESS", Effect.of("conciliated", "CONCILIATION_COMPLETE", "FINAL_DECISION")
                .closure("CONCILIATION_SUCCESS").fx(FX_RESOLVED_AT).fx(FX_CONCILIATION_SUCCESS));

        // Status is "escalated", NOT a "conciliation_failed" value. Pinned by test; the STAGE carries
        // the failure and the status says where the file went.
        EFFECTS.put("CONCILIATION_FAILED", Effect.of("escalated", "CONCILIATION_FAILED", "FINAL_DECISION")
                .assign(RbioRoles.ADJUDICATOR, ROUND_ROBIN).sla("ADJUDICATION").fx(FX_CONCILIATION_FAILED));

        // requiredParams is the award-amount refusal, moved from a hand-written guard into the table.
        // The pipe means "at least one of": the adjudication screen sends compensationAmount while the
        // API contract says awardAmount, and reading only one recorded a citizen's award as 0.00 on an
        // irreversible statutory act. Absence is REFUSED, never defaulted.
        EFFECTS.put("ADJUDICATION_AWARD", Effect.of("adjudicated", "AWARD_ISSUED", "FINAL_DECISION")
                .closure("ADJUDICATION_AWARD").fx(FX_RESOLVED_AT)
                .require("awardAmount|compensationAmount").fx(FX_AWARD));

        EFFECTS.put("ADJUDICATION_REJECT", Effect.of("rejected", "ADJUDICATION_REJECTED", "FINAL_DECISION")
                .closure("ADJUDICATION_REJECTED").fx(FX_RESOLVED_AT).fx(FX_ADJUDICATION_REJECT));

        EFFECTS.put("ISSUE_NOTICE_13_1", Effect.of(null, "NOTICE_13_1_ISSUED", "FINAL_DECISION").fx(FX_NOTICE_13_1));

        EFFECTS.put("IMPLEAD_PARTY", Effect.of(null, "PARTY_IMPLEADED", "FINAL_DECISION")
                .require("partyName").fx(FX_IMPLEAD));

        EFFECTS.put("REASSIGN", Effect.of("assigned", "REASSIGNED", null)
                .assign(null, TARGET_PARAM).fx(FX_REASSIGN_ROLE));

        EFFECTS.put("CLOSE_COMPLAINT", Effect.of("closed", "CLOSED", "FINAL_DECISION")
                .closure("ADMIN_CLOSED").fx(FX_CLOSURE_FROM_PARAM).fx(FX_RESOLVED_AT).fx(FX_CLOSED_AT).terminal());

        EFFECTS.put("REOPEN", Effect.of("in_progress", "REOPENED", "ASSESSMENT")
                .sla("OFFICER_ASSESSMENT").fx(FX_REOPEN));

        EFFECTS.put("BULK_ASSIGN", Effect.of("assigned", "BULK_ASSIGNED", null)
                .assign(RbioRoles.OFFICER, TARGET_PARAM).fx(FX_BULK_ROLE));
    }

    /**
     * Role → permitted actions, reproducing the old {@code ROLE_ACTIONS} map EXACTLY for the five
     * legacy roles. {@code RbioWorkflowServiceTest} asserts this matrix cell by cell and the NEGATIVE
     * cells matter as much as the positive ones — granting a role one extra action here fails a test
     * that exists precisely to catch privilege creep.
     */
    private static final Map<String, Set<String>> ROLE_ACTIONS = new LinkedHashMap<>();

    static {
        ROLE_ACTIONS.put(RbioRoles.OFFICER, Set.of(
                "ACCEPT", "TAKE_ACTION", "RESOLVE", "REJECT", "ESCALATE",
                "REQUEST_INFO", "SCHEDULE_MEETING", "FORWARD_TO_CONCILIATION", "ISSUE_ADVISORY"));
        ROLE_ACTIONS.put(RbioRoles.SUPERVISOR, Set.of(
                "APPROVE", "RETURN_TO_OFFICER", "RESOLVE", "ESCALATE",
                "FORWARD_TO_ADJUDICATION", "FORWARD_TO_CONCILIATION", "REASSIGN", "ISSUE_ADVISORY"));
        ROLE_ACTIONS.put(RbioRoles.CONCILIATOR, Set.of(
                "CONCILIATION_SUCCESS", "CONCILIATION_FAILED", "SCHEDULE_MEETING", "ESCALATE_TO_ADJUDICATION"));
        ROLE_ACTIONS.put(RbioRoles.ADJUDICATOR, Set.of(
                "ADJUDICATION_AWARD", "ADJUDICATION_REJECT", "ISSUE_NOTICE_13_1", "IMPLEAD_PARTY"));
        ROLE_ACTIONS.put(RbioRoles.ADMIN, Set.of(
                "REASSIGN", "ESCALATE", "CLOSE_COMPLAINT", "REOPEN", "BULK_ASSIGN"));

        // The four new ranks. Mirroring the nearest legacy equivalent makes them usable on arrival
        // instead of having every action refused; S1-S7 refine them per story.
        //
        // ADJUDICATION_AWARD is OMBUDSMAN-only and deliberately NOT granted to the Deputy: a Deputy
        // decides within DELEGATED authority, so an unbounded award there would hand a delegated
        // officer the Ombudsman's own statutory power.
        ROLE_ACTIONS.put(RbioRoles.DEALING_OFFICIAL, ROLE_ACTIONS.get(RbioRoles.OFFICER));
        ROLE_ACTIONS.put(RbioRoles.REVIEWER, ROLE_ACTIONS.get(RbioRoles.SUPERVISOR));
        ROLE_ACTIONS.put(RbioRoles.DEPUTY_OMBUDSMAN, Set.of(
                "APPROVE", "RETURN_TO_OFFICER", "RESOLVE", "ESCALATE", "REASSIGN", "ISSUE_ADVISORY",
                "FORWARD_TO_CONCILIATION", "FORWARD_TO_ADJUDICATION", "SCHEDULE_MEETING"));
        ROLE_ACTIONS.put(RbioRoles.OMBUDSMAN, Set.of(
                "APPROVE", "RETURN_TO_OFFICER", "RESOLVE", "REJECT", "REASSIGN", "ISSUE_ADVISORY",
                "FORWARD_TO_CONCILIATION", "FORWARD_TO_ADJUDICATION", "SCHEDULE_MEETING",
                "ADJUDICATION_AWARD", "ADJUDICATION_REJECT", "ISSUE_NOTICE_13_1", "IMPLEAD_PARTY",
                "CLOSE_COMPLAINT", "REOPEN"));
    }

    /**
     * From-statuses per action, reproducing {@code isActionValidForState} EXACTLY.
     * ADVERTISEMENT ONLY — see the class comment. An action absent here was {@code default: return true}
     * in the old switch, i.e. valid from any status.
     */
    private static final Map<String, List<String>> VALID_FROM = new LinkedHashMap<>();

    static {
        VALID_FROM.put("ACCEPT",      List.of("assigned", "returned"));
        VALID_FROM.put("TAKE_ACTION", List.of("assigned", "returned"));
        VALID_FROM.put("APPROVE",     List.of("in_progress"));
        VALID_FROM.put("REJECT",      List.of("in_progress", "assigned"));
        VALID_FROM.put("ESCALATE",    List.of("in_progress", "assigned"));
        VALID_FROM.put("RETURN_TO_OFFICER",        List.of("escalated", "in_progress"));
        VALID_FROM.put("RESOLVE",                  List.of("in_progress"));
        VALID_FROM.put("REQUEST_INFO",             List.of("in_progress", "assigned", "conciliation"));
        VALID_FROM.put("SCHEDULE_MEETING",         List.of("in_progress", "assigned", "conciliation"));
        VALID_FROM.put("ISSUE_ADVISORY",           List.of("in_progress", "assigned"));
        VALID_FROM.put("FORWARD_TO_CONCILIATION",  List.of("in_progress", "escalated"));
        VALID_FROM.put("FORWARD_TO_ADJUDICATION",  List.of("in_progress", "escalated"));
        VALID_FROM.put("ESCALATE_TO_ADJUDICATION", List.of("conciliation", "escalated"));
        VALID_FROM.put("CONCILIATION_SUCCESS",     List.of("conciliation"));
        VALID_FROM.put("CONCILIATION_FAILED",      List.of("conciliation"));
        VALID_FROM.put("ADJUDICATION_AWARD",       List.of("adjudication"));
        VALID_FROM.put("ADJUDICATION_REJECT",      List.of("adjudication"));
        VALID_FROM.put("ISSUE_NOTICE_13_1",        List.of("adjudication"));
        VALID_FROM.put("IMPLEAD_PARTY",            List.of("adjudication"));
        VALID_FROM.put("REOPEN", List.of("closed", "resolved", "adjudicated", "conciliated"));
        // REASSIGN, BULK_ASSIGN and CLOSE_COMPLAINT were NOT-IN exclusions, not enumerations. They are
        // any-status rows and the exclusion is applied by validForState, because enumerating "every
        // status except six" would silently omit any status a later session introduces.
    }

    /** Actions whose validity is an exclusion. Mirrors the old NOT-IN arms verbatim. */
    private static final Map<String, Set<String>> EXCLUDED_FROM = Map.of(
            "REASSIGN",        Set.of("closed", "resolved", "rejected", "withdrawn", "adjudicated", "conciliated"),
            "BULK_ASSIGN",     Set.of("closed", "resolved", "rejected", "withdrawn", "adjudicated", "conciliated"),
            "CLOSE_COMPLAINT", Set.of("closed"));

    /** Every action any role may perform. */
    public static Set<String> allActions() {
        Set<String> all = new LinkedHashSet<>();
        ROLE_ACTIONS.values().forEach(all::addAll);
        return all;
    }

    public static Set<String> actionsFor(String role) {
        if (role == null) return Set.of();
        return ROLE_ACTIONS.getOrDefault(role.toUpperCase(Locale.ROOT), Set.of());
    }

    /**
     * The transition for an action performed by a role, or null when the role may not perform it.
     *
     * <p>Does NOT consider the current status: {@code performAction} never did. Status governs
     * advertisement via {@link #validForState}.
     */
    public static RbioWorkflowTransition resolve(String action, String role) {
        if (action == null || role == null) return null;
        String upperAction = action.toUpperCase(Locale.ROOT);
        if (!actionsFor(role).contains(upperAction)) return null;
        Effect effect = EFFECTS.get(upperAction);
        return effect == null ? null : effect.toRow(upperAction, role.toUpperCase(Locale.ROOT), null);
    }

    /**
     * The effect of an action irrespective of role, for the un-scoped execution path.
     *
     * <p>Needed because {@code performAction} accepts a BLANK userRole and still executes — the role
     * check is skipped, not failed, when no role is supplied. Callers with no role therefore need the
     * effect without a role lookup.
     */
    public static RbioWorkflowTransition effectOf(String action) {
        if (action == null) return null;
        String upperAction = action.toUpperCase(Locale.ROOT);
        Effect effect = EFFECTS.get(upperAction);
        return effect == null ? null : effect.toRow(upperAction, "", null);
    }

    /** Whether the action should be OFFERED for a complaint currently in {@code status}. */
    public static boolean validForState(String action, String status) {
        if (action == null || status == null) return false;
        String upperAction = action.toUpperCase(Locale.ROOT);

        Set<String> excluded = EXCLUDED_FROM.get(upperAction);
        if (excluded != null) return !excluded.contains(status);

        List<String> froms = VALID_FROM.get(upperAction);
        if (froms == null) return true;
        return froms.contains(status);
    }

    /** Every row to seed, one per (action, role, from-status). */
    public static List<RbioWorkflowTransition> allRows() {
        List<RbioWorkflowTransition> rows = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : ROLE_ACTIONS.entrySet()) {
            String role = entry.getKey();
            for (String action : entry.getValue()) {
                Effect effect = EFFECTS.get(action);
                if (effect == null) continue;
                List<String> froms = VALID_FROM.get(action);
                if (froms == null || froms.isEmpty()) {
                    rows.add(effect.toRow(action, role, null));
                } else {
                    for (String from : froms) {
                        rows.add(effect.toRow(action, role, from));
                    }
                }
            }
        }
        return rows;
    }

    /** A declarative action effect, expanded into rows. */
    private static final class Effect {
        private final String toStatus;
        private final String toStage;
        private final String toMilestone;
        private String assignToRole;
        private String assignStrategy;
        private String slaStage;
        private String closureCause;
        private String requiredParams;
        private String sideEffect;
        private boolean terminal;

        private Effect(String toStatus, String toStage, String toMilestone) {
            this.toStatus = toStatus;
            this.toStage = toStage;
            this.toMilestone = toMilestone;
        }

        static Effect of(String toStatus, String toStage, String toMilestone) {
            return new Effect(toStatus, toStage, toMilestone);
        }

        Effect assign(String role, String strategy) {
            this.assignToRole = role;
            this.assignStrategy = strategy;
            return this;
        }

        Effect sla(String stage) {
            this.slaStage = stage;
            return this;
        }

        Effect closure(String cause) {
            this.closureCause = cause;
            return this;
        }

        Effect require(String params) {
            this.requiredParams = params;
            return this;
        }

        Effect fx(String name) {
            this.sideEffect = (sideEffect == null || sideEffect.isBlank()) ? name : sideEffect + "," + name;
            return this;
        }

        Effect terminal() {
            this.terminal = true;
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
                    .requiresComment("N")
                    .displayOrder(999)
                    .isActive("Y")
                    .schemeVersion("RBIOS_2021")
                    .ownedBy("WAVE0")
                    .build();
        }
    }

    private RbioTransitionRegistry() {
    }
}
