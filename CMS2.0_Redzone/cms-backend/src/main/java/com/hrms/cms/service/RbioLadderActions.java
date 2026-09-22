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
 * The S3 ladder and milestone actions: the handovers up the hierarchy, the three role-specific
 * send-backs, and the decision actions that conclude a complaint.
 *
 * <p><b>Why these are declared here and not added to {@link RbioTransitionRegistry}.</b> That class is
 * Wave 0's, and its role/action matrix is asserted cell by cell — including the NEGATIVE cells — by
 * {@code RbioWorkflowServiceTest}, precisely to catch privilege creep. Adding rows to it would edit
 * another session's file and change tests that exist to detect exactly that kind of change. This class is
 * additive: the service resolves from the DATABASE first, and these rows are seeded into it by
 * {@code RbioLadderTransitionSeeder} at {@code @Order(32)}.
 *
 * <p><b>What was actually broken.</b> Fifteen action codes the frontend can POST had no server
 * counterpart, so they hit {@code "Unknown RBIO action"} and every failure path is
 * {@code catchError(() => of(default))} — the screens reported success and nothing happened. The worst
 * were the send-backs: {@code SEND_BACK_DO}, {@code SEND_BACK_REVIEWER} and {@code SEND_BACK_INCHARGE}
 * are dispatched from {@code task-action.component.ts} on the RBIO route, where only a single generic
 * {@code RETURN_TO_OFFICER} existed with no notion of WHICH role to return to.
 *
 * <p><b>The one legal boundary held here.</b> A maintainability decision is a citizen-facing statutory
 * determination. These rows carry the STATE MACHINE for it, and deliberately carry NO clause citation:
 * the clause a complaint is closed under comes from the closure-clause master
 * ({@code closureClause} param, validated against CLOSURE_CLAUSE_MASTER), never from a literal compiled
 * in here. Inventing a clause number would be worse than refusing the action.
 */
public final class RbioLadderActions {

    /** Owner tag on every row this class seeds, so its rows are identifiable in the table. */
    private static final String OWNER = "S3";

    // ── Ladder handovers (UST477, 481, 514, 515, 485) ──
    public static final String SUBMIT_FOR_REVIEW           = "SUBMIT_FOR_REVIEW";
    public static final String FORWARD_TO_DEPUTY_OMBUDSMAN = "FORWARD_TO_DEPUTY_OMBUDSMAN";
    public static final String FORWARD_TO_OMBUDSMAN        = "FORWARD_TO_OMBUDSMAN";

    // ── Send-backs (UST523/524/529/530, 516-517, 531) ──
    public static final String SEND_BACK_DO       = "SEND_BACK_DO";
    public static final String SEND_BACK_REVIEWER = "SEND_BACK_REVIEWER";
    public static final String SEND_BACK_DEPUTY   = "SEND_BACK_DEPUTY";

    /**
     * The name {@code task-action.component.ts} sends for "send back to the officer below".
     *
     * <p>Kept as a distinct action rather than silently rewritten to {@link #SEND_BACK_DEPUTY}, because
     * the CEPC ladder it was copied from really does have an Incharge rank and RBIO does not. Mapping it
     * to the equivalent RBIO rung is a routing decision, so it is declared as one instead of hidden in a
     * string replacement.
     */
    public static final String SEND_BACK_INCHARGE = "SEND_BACK_INCHARGE";

    // ── Decision actions ──
    public static final String DEPUTY_OMBUDSMAN_DECISION = "DEPUTY_OMBUDSMAN_DECISION";
    public static final String DECIDE_MAINTAINABLE       = "DECIDE_MAINTAINABLE";
    public static final String DECIDE_NON_MAINTAINABLE   = "DECIDE_NON_MAINTAINABLE";
    public static final String ADVISORY_COMPLIED         = "ADVISORY_COMPLIED";
    public static final String FACILITATION              = "FACILITATION";
    public static final String SETTLED                   = "SETTLED";
    public static final String WITHDRAWN                 = "WITHDRAWN";
    public static final String NOT_A_COMPLAINT           = "NOT_A_COMPLAINT";
    public static final String FORWARD_TO_REGULATORY_BODY = "FORWARD_TO_REGULATORY_BODY";

    /**
     * The transposed alias of Wave 0's {@code ISSUE_NOTICE_13_1}.
     *
     * <p>{@code rbio-adjudication.component.ts:121} sends {@code ISSUE_13_1_NOTICE}. Rather than edit
     * that component — a statutory notice is not something to risk on a rename — the server accepts both
     * spellings for the same effect. The alias is data, so it is visible in the table rather than buried
     * in a string comparison.
     */
    public static final String ISSUE_13_1_NOTICE_ALIAS = "ISSUE_13_1_NOTICE";

    /** Side effects this class introduces, applied by {@code RbioWorkflowService}. */
    public static final String FX_MAINTAINABILITY = "MAINTAINABILITY";
    public static final String FX_ADVISORY_COMPLIED = "ADVISORY_COMPLIED_FLAG";
    public static final String FX_REGULATORY_BODY = "REGULATORY_BODY";
    public static final String FX_DEPUTY_DECISION = "DEPUTY_DECISION";

    /**
     * Every declared effect, keyed by action.
     *
     * <p>{@code requiresComment} is set on each row where a comment is mandatory. UST516-517 and 531
     * require it on every send-back, and it is set on the decision actions too: a determination that
     * changes a citizen's statutory position with no recorded reason is not auditable. The column existed
     * from Wave 0 but had no enforcement anywhere — {@code RbioWorkflowService.requireComment} is the
     * other half.
     */
    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        // ═══════════════ Upward handovers ═══════════════
        // ROUND_ROBIN is the DEFAULT strategy, which is what "Automatic (Round Robin) pre-selected"
        // means server-side (UST477/481/514). A caller who names targetUser overrides it — that is the
        // Manual option — and TARGET_PARAM is not needed as a separate row because applyAssignee treats
        // an explicit target as winning for PREVIOUS_HOLDER and TARGET_PARAM alike.
        SPECS.put(SUBMIT_FOR_REVIEW, Spec.of("reviewer_review", "REVIEWER_REVIEW", "ASSESSMENT")
                .assign(RbioRoles.REVIEWER, RbioTransitionRegistry.ROUND_ROBIN)
                .sla("REVIEWER_REVIEW")
                .from("in_progress", "assigned", "returned")
                .roles(RbioRoles.DEALING_OFFICIAL, RbioRoles.OFFICER));

        SPECS.put(FORWARD_TO_DEPUTY_OMBUDSMAN, Spec.of("deputy_review", "DEPUTY_OMBUDSMAN_REVIEW", "ASSESSMENT")
                .assign(RbioRoles.DEPUTY_OMBUDSMAN, RbioTransitionRegistry.ROUND_ROBIN)
                .sla("DEPUTY_REVIEW")
                .from("reviewer_review", "in_progress")
                .roles(RbioRoles.REVIEWER, RbioRoles.SUPERVISOR));

        // UST515/485 have NO manual option: the office Ombudsman is assigned automatically on save.
        // There is one Ombudsman per office, so round-robin over that role resolves to them.
        SPECS.put(FORWARD_TO_OMBUDSMAN, Spec.of("ombudsman_review", "OMBUDSMAN_REVIEW", "FINAL_DECISION")
                .assign(RbioRoles.OMBUDSMAN, RbioTransitionRegistry.ROUND_ROBIN)
                .sla("OMBUDSMAN_REVIEW")
                .from("deputy_review", "reviewer_review", "in_progress")
                .roles(RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.REVIEWER));

        // ═══════════════ Send-backs ═══════════════
        // PREVIOUS_HOLDER, not ROUND_ROBIN: a send-back returns the file to the officer who previously
        // held that rank. When no active previous holder exists the action is REFUSED so the sender must
        // choose, rather than the file landing on an arbitrary holder of the role or — as in CEPC —
        // staying with the sender while its role silently drops a rung.
        SPECS.put(SEND_BACK_DO, Spec.of("returned", "SENT_BACK_TO_DO", "ASSESSMENT")
                .assign(RbioRoles.DEALING_OFFICIAL, RbioTransitionRegistry.PREVIOUS_HOLDER)
                .comment()
                .sla("OFFICER_ASSESSMENT")
                .from("reviewer_review", "deputy_review", "ombudsman_review", "in_progress", "escalated")
                .roles(RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN,
                        RbioRoles.SUPERVISOR));

        SPECS.put(SEND_BACK_REVIEWER, Spec.of("reviewer_review", "SENT_BACK_TO_REVIEWER", "ASSESSMENT")
                .assign(RbioRoles.REVIEWER, RbioTransitionRegistry.PREVIOUS_HOLDER)
                .comment()
                .sla("REVIEWER_REVIEW")
                .from("deputy_review", "ombudsman_review")
                .roles(RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN));

        SPECS.put(SEND_BACK_DEPUTY, Spec.of("deputy_review", "SENT_BACK_TO_DEPUTY", "ASSESSMENT")
                .assign(RbioRoles.DEPUTY_OMBUDSMAN, RbioTransitionRegistry.PREVIOUS_HOLDER)
                .comment()
                .sla("DEPUTY_REVIEW")
                .from("ombudsman_review")
                .roles(RbioRoles.OMBUDSMAN));

        // The CEPC-vocabulary name the shared task-action screen sends on the RBIO route. RBIO has no
        // Incharge rank; the equivalent rung below a Closing Authority is the Deputy Ombudsman.
        SPECS.put(SEND_BACK_INCHARGE, Spec.of("deputy_review", "SENT_BACK_TO_DEPUTY", "ASSESSMENT")
                .assign(RbioRoles.DEPUTY_OMBUDSMAN, RbioTransitionRegistry.PREVIOUS_HOLDER)
                .comment()
                .sla("DEPUTY_REVIEW")
                .from("ombudsman_review", "deputy_review")
                .roles(RbioRoles.OMBUDSMAN, RbioRoles.DEPUTY_OMBUDSMAN));

        // ═══════════════ Maintainability (UST476, 482, 486, 532) ═══════════════
        // A MAINTAINABLE decision keeps the file with its current holder and only records the finding —
        // toStatus is null, meaning unchanged. It is the NON_MAINTAINABLE branch that closes.
        SPECS.put(DECIDE_MAINTAINABLE, Spec.of(null, "MAINTAINABILITY_DECIDED", "ASSESSMENT")
                .comment()
                .fx(FX_MAINTAINABILITY)
                .from("in_progress", "reviewer_review", "deputy_review", "ombudsman_review")
                .roles(RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
                        RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR));

        // closureClause is REQUIRED, not defaulted. A non-maintainable closure denies a citizen statutory
        // recourse, so it may not be recorded without the clause it was made under — and the clause is
        // supplied and validated against the master table, never compiled in here.
        SPECS.put(DECIDE_NON_MAINTAINABLE, Spec.of("closed", "NON_MAINTAINABLE", "FINAL_DECISION")
                .closure("NON_MAINTAINABLE")
                .require("closureClause")
                .comment()
                .fx(FX_MAINTAINABILITY)
                .fx(RbioTransitionRegistry.FX_RESOLVED_AT)
                .fx(RbioTransitionRegistry.FX_CLOSED_AT)
                .terminal()
                .from("in_progress", "reviewer_review", "deputy_review", "ombudsman_review")
                .roles(RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN, RbioRoles.SUPERVISOR));

        // ═══════════════ Deputy Ombudsman decision ═══════════════
        // Orphaned in the frontend since it was written (rbio-workflow.service.ts:76). It carries BOTH a
        // maintainability finding and a facilitation/rejection decision, so both params are required
        // rather than defaulted — a decision record missing half its content is not a decision.
        SPECS.put(DEPUTY_OMBUDSMAN_DECISION, Spec.of(null, "DEPUTY_DECISION_RECORDED", "ASSESSMENT")
                .require("maintainability")
                .require("decision")
                .comment()
                .fx(FX_DEPUTY_DECISION)
                .from("deputy_review", "in_progress", "reviewer_review")
                .roles(RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN));

        // ═══════════════ Outcome actions (UST535-538 and the milestone set) ═══════════════
        SPECS.put(ADVISORY_COMPLIED, Spec.of("closed", "ADVISORY_COMPLIED", "FINAL_DECISION")
                .closure("ADVISORY_COMPLIED")
                .comment()
                .fx(FX_ADVISORY_COMPLIED)
                .fx(RbioTransitionRegistry.FX_RESOLVED_AT)
                .fx(RbioTransitionRegistry.FX_CLOSED_AT)
                .terminal()
                .from("advisory_issued")
                .roles(RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
                        RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR));

        SPECS.put(FACILITATION, Spec.of("resolved", "FACILITATED", "FINAL_DECISION")
                .closure("FACILITATION")
                .comment()
                .fx(RbioTransitionRegistry.FX_RESOLVED_AT)
                .from("in_progress", "conciliation", "deputy_review", "ombudsman_review")
                .roles(RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
                        RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR));

        SPECS.put(SETTLED, Spec.of("resolved", "SETTLED", "FINAL_DECISION")
                .closure("SETTLED")
                .comment()
                .fx(RbioTransitionRegistry.FX_RESOLVED_AT)
                .from("in_progress", "conciliation", "deputy_review", "ombudsman_review")
                .roles(RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
                        RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR));

        // The RBIO-side record of a withdrawal. The citizen-facing withdrawal endpoint is separate and
        // owned elsewhere; this exists so an officer can record one that arrived by letter or in person.
        SPECS.put(WITHDRAWN, Spec.of("withdrawn", "WITHDRAWN", "FINAL_DECISION")
                .closure("WITHDRAWN")
                .comment()
                .fx(RbioTransitionRegistry.FX_RESOLVED_AT)
                .fx(RbioTransitionRegistry.FX_CLOSED_AT)
                .terminal()
                .from("in_progress", "assigned", "reviewer_review", "deputy_review", "ombudsman_review",
                        "conciliation")
                .roles(RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
                        RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR));

        SPECS.put(NOT_A_COMPLAINT, Spec.of("closed", "NOT_A_COMPLAINT", "FINAL_DECISION")
                .closure("NOT_A_COMPLAINT")
                .require("closureClause")
                .comment()
                .fx(RbioTransitionRegistry.FX_RESOLVED_AT)
                .fx(RbioTransitionRegistry.FX_CLOSED_AT)
                .terminal()
                .from("in_progress", "assigned", "reviewer_review", "deputy_review", "ombudsman_review")
                .roles(RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN, RbioRoles.SUPERVISOR));

        // Orphaned in the frontend (rbio-workflow.service.ts:179). The body it names is recorded rather
        // than stuffed into assignedOfficer — the shape CEPC uses for its external forwards, which writes
        // a non-user string into a user column.
        SPECS.put(FORWARD_TO_REGULATORY_BODY, Spec.of("forwarded_external", "FORWARDED_REGULATORY_BODY", "FORWARD")
                .require("regulatoryBodyName|regulatoryBodyId")
                .comment()
                .fx(FX_REGULATORY_BODY)
                .from("in_progress", "reviewer_review", "deputy_review", "ombudsman_review", "escalated")
                .roles(RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN, RbioRoles.SUPERVISOR));

        // ═══════════════ Alias ═══════════════
        // Same effect as Wave 0's ISSUE_NOTICE_13_1, including the null status: the notice moves the stage
        // and stamps a date, it does not change where the complaint stands.
        SPECS.put(ISSUE_13_1_NOTICE_ALIAS, Spec.of(null, "NOTICE_13_1_ISSUED", "FINAL_DECISION")
                .fx(RbioTransitionRegistry.FX_NOTICE_13_1)
                .from("adjudication")
                .roles(RbioRoles.ADJUDICATOR, RbioRoles.OMBUDSMAN));
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

    private RbioLadderActions() {
    }
}
