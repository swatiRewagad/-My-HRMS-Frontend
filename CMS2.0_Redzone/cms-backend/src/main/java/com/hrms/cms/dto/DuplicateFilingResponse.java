package com.hrms.cms.dto;

import java.util.List;

/**
 * The wire contract for duplicate detection and the repeat-complainant signal.
 *
 * <h2>THE PII BOUNDARY IS THIS TYPE</h2>
 * This feature surfaces one identified person's OTHER complaints, which makes it the most PII-sensitive
 * read in the assistance set, and this repo has already had to fix leaks in two endpoints. The control is
 * the SHAPE rather than a masking call: there is no field here for a complainant name, phone, email,
 * address, account number or card number, and no field for the subject or description of another
 * complaint. Not masked — ABSENT. A serialiser cannot leak what the record has no component for, and a
 * future caller cannot opt into more by passing a flag, because there is no flag and nothing to opt into.
 *
 * <p>The brief permits a SNIPPET of the narrative and this contract declines even that. A snippet from
 * someone's other complaint is the single most identifying thing that could appear on the screen — free
 * text is where names, account numbers and addresses actually live in this register — and the signal does
 * not need it to be useful. "3 earlier complaints against HDFC Bank in the last 30 days, here are their
 * numbers and dates" is actionable on its own, and an officer who needs the narrative opens the complaint,
 * where the existing per-screen PII controls ({@code PiiMaskingService}, {@code PiiRevealService}) apply
 * and the access is attributable.
 *
 * <p>{@link Match} carries exactly FIVE fields and each is justified individually there.
 *
 * <h2>Always a usable payload, never a 4xx</h2>
 * {@link #empty} is the single degraded shape: an unknown complaint, a disabled feature, an unapplied
 * migration, a caller whose roles map to no department, a query timeout and an internal failure all yield
 * it at HTTP 200. {@code GlobalExceptionHandler} maps a bare {@code RuntimeException} to 400, so a leaked
 * failure here would not read as a quiet signal but as a client error on a valid request — and the screen
 * this sits beside must stay usable regardless.
 *
 * <h2>Every signal carries its DENOMINATOR</h2>
 * {@code §5.1}'s rule is that "a bare recommendation with no denominator will be distrusted, correctly".
 * So {@link #duplicateCount} never travels without {@link #windowDays} and the matches that produced it,
 * and {@link RepeatComplainant#lifetimeFilings} never travels without
 * {@link RepeatComplainant#nonMaintainableClosures}. There is no field for a score, a risk level, a
 * percentile or a verdict, and that is structural: the statutory {@code 16(2)(b)} "frivolous or
 * vexatious" determination belongs to the officer, and a server that returned "VEXATIOUS: HIGH" would be
 * making it for them while appearing merely to inform. The type cannot express a verdict.
 *
 * @param complaintNumber the complaint the officer is working — echoed so a client cannot mis-attribute
 *                        a late response to the wrong screen
 * @param available       whether the feature was switched ON for this read. Distinguishes "no duplicates
 *                        found" from "not asked", which are different facts an officer may need: the
 *                        first is evidence, the second is nothing. A client rendering "no earlier
 *                        complaints" on a disabled feature would be asserting something the server never
 *                        checked.
 * @param windowDays      the duplicate window in force, in days. THE DENOMINATOR of
 *                        {@link #duplicateCount}, and sent rather than assumed because the window is
 *                        configurable: a client hardcoding "30 days" in its sentence would go on saying 30
 *                        after an operator set it to 14, and the officer reading it has no way to tell.
 * @param duplicateCount  how many earlier complaints by this complainant against THIS entity fall inside
 *                        the window. Equal to {@code matches.size()}, and sent explicitly so the number in
 *                        the localised sentence and the number of rows rendered cannot disagree.
 * @param matches         the earlier complaints, newest first. May be empty while {@code available} is
 *                        true — that is the common case (94% of the register) and it is a real answer.
 * @param repeat          the lifetime filing picture, or null when there is no complainant identity to
 *                        speak about. Null rather than a zero-filled record, because "this complainant
 *                        has 0 lifetime filings" is false for every complainant who exists — they have at
 *                        least the one on screen — so a zeroed record could only ever be a lie.
 */
public record DuplicateFilingResponse(String complaintNumber,
                                      boolean available,
                                      int windowDays,
                                      int duplicateCount,
                                      List<Match> matches,
                                      RepeatComplainant repeat) {

    /**
     * ONE earlier complaint by the same complainant. FIVE fields, each justified.
     *
     * <p>This is the record that would leak if any record here did, so the argument for every component
     * is written down rather than assumed:
     *
     * <ul>
     *   <li>{@code complaintNumber} — SAFE and necessary. It is an opaque register identifier, not
     *       personal data, and without it the signal is unactionable: an officer told "3 earlier
     *       complaints" and given no way to find them cannot verify the claim, which is worse than
     *       silence. Opening it goes through the normal complaint screen, where authorisation and PII
     *       masking already apply — so this field grants no access, it only names a case. Note the
     *       contrast with returning the narrative: a number the officer must deliberately open is an
     *       attributable access, whereas a snippet rendered beside the signal is an unattributable one.
     *   <li>{@code filedAt} — SAFE and necessary. A date is the duplicate signal's whole substance ("in
     *       the last 30 days") and the officer cannot judge whether a repeat filing is abusive or simply
     *       a citizen chasing an unanswered complaint without knowing when it was filed. An ISO date-time
     *       string, not a locale-formatted one, so the client decides presentation.
     *   <li>{@code entityKey} — SAFE. The regulated entity, canonicalised. It is the name of a BANK, not
     *       of a person, and it is already on the screen for the complaint being worked — the match was
     *       selected BY it. Carried anyway rather than assumed, because the lifetime list in
     *       {@link RepeatComplainant} may legitimately span entities and a row with no entity label would
     *       be unreadable there.
     *   <li>{@code status} — SAFE. A workflow state from a controlled vocabulary. It is what tells an
     *       officer whether the earlier complaint is still open (so this is double-handling to be merged)
     *       or closed (so this may be a re-filing), which is the actual decision the signal supports.
     *   <li>{@code department} — SAFE, and it is the SCOPE evidence. Every match returned has already
     *       passed the department filter, so this field is the officer's confirmation of that rather than
     *       a widening of it. Returning a department the caller is not in is impossible by construction —
     *       see {@code DuplicateFilingDetectionService}.
     * </ul>
     *
     * <p>WHAT IS DELIBERATELY ABSENT, so a future reader does not add it back as an improvement: the
     * complainant's name, email, phone, address, account and card numbers; the subject; the description;
     * any snippet of either; the assigned officer's identity; and any free-text remark. Each was
     * considered. The complainant's name was the most tempting — it would let an officer confirm the
     * match is really the same person — and it is refused precisely because the feature's own identity
     * rule is contact-based: showing the name would invite the officer to treat a name collision as
     * confirmation, when the server already knows the contact matched exactly. Nothing here is identifying
     * beyond what the officer already has on the screen in front of them.
     *
     * @param complaintNumber the register identifier, so the officer can open the case properly
     * @param filedAt         ISO-8601 date-time; the signal's substance
     * @param entityKey       the canonicalised regulated entity, never a person
     * @param status          the workflow state, from a controlled vocabulary
     * @param department      the owning department; scope evidence, already filtered
     */
    public record Match(String complaintNumber,
                        String filedAt,
                        String entityKey,
                        String status,
                        String department) {
    }

    /**
     * The repeat-complainant picture: the {@code 16(2)(b)} evidence, as COUNTS and never a verdict.
     *
     * <p>{@code §5.3}'s "frivolous or vexatious" determination under clause {@code 16(2)(b)} is the
     * officer's to make, and today they assemble this by hand. This record hands them the two numbers and
     * stops. There is deliberately no {@code vexatious} boolean, no risk band and no threshold comparison
     * in the response — if the server shipped one, the determination would in practice be made by this
     * constant and merely countersigned by the officer, which is exactly the delegation a statutory
     * discretion may not undergo. The type cannot express it.
     *
     * <h3>The honest weakness, in the contract itself</h3>
     * {@link #nonMaintainableClosures} is MEASURED at 47 rows across the whole register by any of the
     * three available markers, of which only 6 belong to a complainant with more than one filing. So this
     * field will be 0 for almost every complainant, and a client must not render "0 of 8 closed as
     * non-maintainable" as though it were evidence AGAINST a {@code 16(2)(b)} determination — it is
     * evidence that the determination is not being recorded in the register. {@link #scopeLimited} exists
     * partly so a client can say so honestly.
     *
     * @param lifetimeFilings       how many complaints this complainant has filed in total, INCLUDING the
     *                              one on screen, within the caller's department scope. The denominator of
     *                              the whole signal. Includes the current complaint deliberately: "this is
     *                              their 8th filing" is the sentence an officer needs, and a count that
     *                              excluded the case in hand would be off by one against the list the
     *                              officer can see.
     * @param nonMaintainableClosures how many of those were closed as non-maintainable. A SUBSET of
     *                              {@code lifetimeFilings}, never a second bucket — a client that added
     *                              the two would double-count.
     * @param windowFilings         how many fell inside {@link DuplicateFilingResponse#windowDays},
     *                              against ANY entity. Distinct from {@code duplicateCount}, which is
     *                              same-entity only: a complainant filing against six different banks in a
     *                              week is a different pattern from one filing six times about one bank,
     *                              and collapsing them would hide which.
     * @param scopeLimited          true when candidate rows were REMOVED by the department scope filter,
     *                              so the counts above are a view of this officer's department and not of
     *                              the register. Load-bearing for honesty: without it an officer would
     *                              read a scoped count as a lifetime total and could under-state a
     *                              complainant's filing history in a {@code 16(2)(b)} note. It reports
     *                              THAT rows were withheld and never how many or which, because the count
     *                              of out-of-scope complaints is itself information about another
     *                              department's caseload.
     */
    public record RepeatComplainant(int lifetimeFilings,
                                    int nonMaintainableClosures,
                                    int windowFilings,
                                    boolean scopeLimited) {
    }

    /**
     * The single degraded shape: no signal, HTTP 200, feature reported as unavailable.
     *
     * <p>Used for a disabled switch, an unknown complaint, an unapplied migration, a caller whose roles
     * map to no department, a timeout and any internal failure. They are collapsed deliberately: a client
     * that could tell "disabled" from "failed" would be tempted to render the difference, and an officer
     * does not benefit from being told which internal reason there is no signal — only from the screen
     * continuing to work.
     *
     * <p>{@code available = false} and an EMPTY match list rather than a null one, so a client iterating
     * the list needs no null check and cannot crash a complaint screen on the degraded path.
     * {@code repeat} IS null, because a zero-filled record would assert that the complainant has no
     * filing history, which is false for everyone — they have at least the complaint on screen.
     */
    public static DuplicateFilingResponse empty(String complaintNumber) {
        return new DuplicateFilingResponse(complaintNumber, false, 0, 0, List.of(), null);
    }

    /**
     * An ENABLED read that found nothing, which is a different fact from {@link #empty}.
     *
     * <p>{@code available = true} with no matches means "we looked and there are none" — the common case,
     * 94% of the register. Distinguishing it from "we did not look" is the whole reason
     * {@link #available} exists: the first is evidence an officer may rely on, the second is not.
     *
     * <p>{@code windowDays} is still carried, because the denominator of a NEGATIVE finding matters as
     * much as of a positive one: "no earlier complaints in the last 30 days" and "...in the last 7 days"
     * are different assurances.
     */
    public static DuplicateFilingResponse none(String complaintNumber, int windowDays,
                                               RepeatComplainant repeat) {
        return new DuplicateFilingResponse(complaintNumber, true, windowDays, 0, List.of(), repeat);
    }
}
