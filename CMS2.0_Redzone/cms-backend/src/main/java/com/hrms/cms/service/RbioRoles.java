package com.hrms.cms.service;

import java.util.List;
import java.util.Map;

/**
 * The RBIO role vocabulary, in one place.
 *
 * <p>Two KINDS of role live here and the distinction is the point of this class:
 *
 * <ul>
 *   <li><b>Ranks</b> — a position in the escalation hierarchy. Dealing Official, Reviewer, Deputy
 *       Ombudsman, Ombudsman. Escalation moves UP this ladder.</li>
 *   <li><b>Stages</b> — a function a ranked officer performs at a point in the process. Conciliator
 *       and Adjudicator. These are NOT ranks: nothing is promoted "to adjudicator", and a
 *       conciliation that fails goes to adjudication because the process says so, not because
 *       adjudication outranks conciliation.</li>
 * </ul>
 *
 * <p><b>Why there are TWO ladders below, and why that is not an oversight.</b> The legacy ladder is
 * {@code OFFICER -> SUPERVISOR -> CONCILIATOR -> ADJUDICATOR}, which conflates the two kinds above —
 * it makes "adjudicator" a promotion from "conciliator". The rank ladder is the corrected one.
 *
 * <p>The legacy ladder is nevertheless reproduced EXACTLY, because it is live behaviour that
 * {@code RbioWorkflowServiceTest} pins: ESCALATE from {@code RBIO_OFFICER} must still yield
 * {@code RBIO_SUPERVISOR}, and APPROVE from {@code RBIO_SUPERVISOR} must still yield
 * {@code RBIO_CONCILIATOR} with status {@code conciliation}. Silently re-pointing those arrows would
 * change where in-flight complaints escalate to — a routing change to live case files, smuggled in
 * under a refactor. Re-pointing them is a decision for the story that introduces the rank ladder into
 * the workflow, which can then migrate the affected records deliberately.
 *
 * <p>So: complaints whose {@code assigned_role} is a legacy role keep escalating along the legacy
 * ladder; complaints on the new ranks use the rank ladder. {@link #nextRank} dispatches on which
 * vocabulary the current role belongs to.
 */
public final class RbioRoles {

    // ── Ranks (new) ──
    public static final String DEALING_OFFICIAL = "RBIO_DEALING_OFFICIAL";
    public static final String REVIEWER         = "RBIO_REVIEWER";
    public static final String DEPUTY_OMBUDSMAN = "RBIO_DEPUTY_OMBUDSMAN";
    public static final String OMBUDSMAN        = "RBIO_OMBUDSMAN";

    // ── Legacy ranks ──
    public static final String OFFICER    = "RBIO_OFFICER";
    public static final String SUPERVISOR = "RBIO_SUPERVISOR";

    // ── Stages, not ranks ──
    public static final String CONCILIATOR = "RBIO_CONCILIATOR";
    public static final String ADJUDICATOR = "RBIO_ADJUDICATOR";

    public static final String ADMIN = "RBIO_ADMIN";

    /**
     * Every RBIO role, for {@code @RbioRoleGuard} lists.
     *
     * <p>Endpoint guards admit the whole vocabulary; which role may perform which ACTION is decided by
     * the transition table, not by the guard. A guard that enumerated only some ranks would refuse a
     * legitimate rank at the HTTP layer before the table could rule on it — which is precisely why
     * RBIO_DEPUTY_OMBUDSMAN and RBIO_OMBUDSMAN got 403s while existing in the frontend.
     */
    public static final String[] ALL = {
            OFFICER, SUPERVISOR, CONCILIATOR, ADJUDICATOR, ADMIN,
            DEALING_OFFICIAL, REVIEWER, DEPUTY_OMBUDSMAN, OMBUDSMAN
    };

    /** Roles that hold and decide a case file. Excludes ADMIN, which administers rather than decides. */
    public static final List<String> CASE_HOLDING_ROLES = List.of(
            DEALING_OFFICIAL, REVIEWER, DEPUTY_OMBUDSMAN, OMBUDSMAN,
            OFFICER, SUPERVISOR, CONCILIATOR, ADJUDICATOR);

    /** The corrected rank ladder. Ombudsman is the top and is absent, so it maps to itself. */
    private static final Map<String, String> RANK_LADDER = Map.of(
            DEALING_OFFICIAL, REVIEWER,
            REVIEWER,         DEPUTY_OMBUDSMAN,
            DEPUTY_OMBUDSMAN, OMBUDSMAN);

    /**
     * The legacy ladder, byte-for-byte as it was in {@code RbioWorkflowService.getNextRole}.
     * Do not "fix" this to point at the rank ladder — see the class comment.
     */
    private static final Map<String, String> LEGACY_LADDER = Map.of(
            OFFICER,     SUPERVISOR,
            SUPERVISOR,  CONCILIATOR,
            CONCILIATOR, ADJUDICATOR);

    /**
     * The role one step above {@code role}, or {@code role} unchanged at the top of either ladder.
     *
     * <p>Null yields {@code RBIO_OFFICER}, matching the previous {@code getNextRole} contract — the
     * legacy default, not the new one, because a null role on a live complaint is a legacy record.
     */
    public static String nextRank(String role) {
        if (role == null) return OFFICER;
        String next = RANK_LADDER.get(role);
        if (next != null) return next;
        return LEGACY_LADDER.getOrDefault(role, role);
    }

    /**
     * The role one step BELOW {@code role}, or {@code role} unchanged at the bottom of either ladder.
     *
     * <p>Derived by inverting {@link #RANK_LADDER} and {@link #LEGACY_LADDER} rather than by declaring a
     * third map, so a send-back can never disagree with the escalation it reverses. Declaring the
     * downward arrows separately would permit ESCALATE and SEND_BACK to describe different ladders.
     *
     * <p>The rank ladder is inverted FIRST, matching {@link #nextRank}'s precedence: a role present in
     * both vocabularies resolves on the corrected ladder in both directions.
     *
     * <p>This answers "which RANK does the file go back to", which is a different question from "which
     * OFFICER held it" — the officer comes from RBIO_CASE_ASSIGNMENT_HISTORY. A send-back needs both: the
     * rank decides authority, the history decides ownership.
     */
    public static String previousRank(String role) {
        if (role == null) return OFFICER;
        String previous = invert(RANK_LADDER, role);
        if (previous != null) return previous;
        previous = invert(LEGACY_LADDER, role);
        return previous != null ? previous : role;
    }

    private static String invert(Map<String, String> ladder, String role) {
        for (Map.Entry<String, String> rung : ladder.entrySet()) {
            if (rung.getValue().equals(role)) {
                return rung.getKey();
            }
        }
        return null;
    }

    private RbioRoles() {
    }
}
