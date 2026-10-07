package com.hrms.cms.dto;

import java.util.List;

/**
 * The closure-clause recommendation payload (Brief 21 §5.3.2).
 *
 * <h2>This response expresses an ORDER and an ANNOTATION. It cannot express a SELECTION.</h2>
 * The brief says "reorder or annotate the existing {@code <select>}", and §5.1 says suggest, never
 * auto-apply. Both are enforced by the SHAPE of this record rather than by a client convention: there
 * is no {@code selected}, no {@code default}, no {@code preferred} and no boolean a client could read as
 * one. A frontend cannot auto-select from this payload without inventing a field, which is the strongest
 * form of "never auto-select" available — the {@code <select>} it annotates commits a real closure, and
 * the precedent is the shipped next-action signal, which ships {@code link: null} for the same reason.
 *
 * <h2>Every recommendation carries its denominator, always</h2>
 * Brief 21 is explicit that "a bare recommendation with no denominator will be distrusted, correctly".
 * {@link Recommendation#occurrences} and {@link Recommendation#cohortTotal} are both non-null on every
 * ranked entry, so "15(1)(a), used in 777 of 815 comparable closures" is the only statement this
 * contract can make. There is no shape here that says "15(1)(a) recommended" with nothing behind it.
 *
 * <h2>The ranking is a PERMUTATION of what the role may cite, never an addition to it</h2>
 * {@code ClosureClauseRecommendationService} builds this by walking the permitted clause list — the one
 * {@code ClosureClauseAccessService} already approved for this role, scheme version and date — and
 * consulting the rollup. A clause present in the rollup but not in that list is unreachable from here.
 * That is how {@code restricted_to_roles} is honoured (§5.3.2, §4) despite the rollup itself being
 * role-blind: it counts what the register DID, and the register contains closures made by roles the
 * current caller is not.
 *
 * <h2>Records rather than a {@code LinkedHashMap}, unlike most controllers here</h2>
 * Because a parallel frontend is written against this exact JSON. A map lets a typo'd key compile and
 * ship; a record makes the contract the compiler's problem. Same reasoning as
 * {@link AssistanceRailResponse}, and the same Jackson behaviour relied on: stable field order, and
 * {@code null} emitted rather than omitted — which matters for {@code occurrences}/{@code cohortTotal},
 * where absent and zero are different claims.
 *
 * <h2>No PII (§4)</h2>
 * Clause codes, labels, two counts and a flag. Nothing here reads or carries a complainant name, email,
 * phone, address or account number, and no field could hold one. The recommendation is a statement about
 * OTHER complaints; the officer is already looking at this one.
 *
 * @param complaintNumber the complaint asked about, echoed back, or null when the picker was opened
 *                        with no complaint in scope
 * @param ranked          true iff at least one entry carries evidence. The client reorders only when
 *                        this is true, so "the rollup is empty" and "the rollup is absent" produce the
 *                        identical, fully-functional unranked picker
 * @param cohortTotal     the denominator shared by every ranked entry, or null when nothing is ranked.
 *                        Carried once at the top as well as on each entry because the client renders a
 *                        single "of N comparable closures" caption and must not have to pick an entry
 *                        to find N
 * @param clauses         every clause the role may cite, ranked entries first. NEVER a subset: a clause
 *                        with no history is not a clause the officer may not use, and dropping it would
 *                        turn a suggestion into a restriction — which is the access service's job
 */
public record ClauseRecommendationResponse(
        String complaintNumber,
        boolean ranked,
        Long cohortTotal,
        List<Recommendation> clauses) {

    /**
     * One clause, with its evidence if the rollup has any.
     *
     * @param clauseCode  the machine key, matching {@code CLOSURE_CLAUSE_MASTER.clause_code} and the
     *                    {@code <option>} values the picker already renders. The client matches on this,
     *                    so it is a contract and not a display string
     * @param label       the master's own English label, carried so a client that does not already hold
     *                    the clause list can render without a second call. NOT authored here — no legal
     *                    text is written in this codebase
     * @param labelKey    the master's translation key for {@code label}, or null. Preferred by the client
     *                    over {@code label}; 13 locales are served and an English literal in a picker is
     *                    a recorded defect elsewhere in this product
     * @param occurrences how many closures in the cohort cited this clause, or null when unranked
     * @param cohortTotal how many closures the cohort contains, or null when unranked. Non-null exactly
     *                    when {@code occurrences} is, because a numerator without a denominator is the
     *                    thing the brief says will be distrusted
     * @param annotated   whether the client should SHOW the counts beside this option. Separate from
     *                    "has counts" on purpose: a clause can be ranked above the historyless tail on
     *                    the strength of a thin row while still not deserving a figure printed next to
     *                    it. The server decides this, so thirteen locales cannot each decide differently
     */
    public record Recommendation(
            String clauseCode,
            String label,
            String labelKey,
            Long occurrences,
            Long cohortTotal,
            boolean annotated) {

        /** A clause the role may cite that the cohort has no row for. Ordered after every ranked one. */
        public static Recommendation unranked(String clauseCode, String label, String labelKey) {
            return new Recommendation(clauseCode, label, labelKey, null, null, false);
        }
    }

    /**
     * The degraded answer: the permitted clauses in the master's own order, with no evidence.
     *
     * <p>Returned for the switch being off, an absent or empty rollup, a query timeout, a cohort below
     * the sample floor, and a picker opened with no complaint. All five are the SAME state as far as the
     * officer is concerned — a working picker that offers no opinion — and collapsing them here is
     * deliberate: a client given five distinguishable failure shapes would end up rendering one of them
     * as an error beside a control that works perfectly.
     */
    public static ClauseRecommendationResponse unranked(String complaintNumber,
                                                       List<Recommendation> clauses) {
        return new ClauseRecommendationResponse(
                complaintNumber, false, null, clauses == null ? List.of() : List.copyOf(clauses));
    }

    /** Nothing to offer at all — the role may cite no clause, or the master is unseeded. */
    public static ClauseRecommendationResponse empty(String complaintNumber) {
        return new ClauseRecommendationResponse(complaintNumber, false, null, List.of());
    }
}
