package com.hrms.cms.dto;

import com.hrms.cms.entity.ClosureClauseMaster;

import java.util.List;

/**
 * The ranked closure-clause picker, as the wire sees it (Brief 21 §5.3.2).
 *
 * <h2>Records, not {@code LinkedHashMap}</h2>
 * Most responses in this module are hand-built maps. This one is a record, for the same reason
 * {@code AssistanceRailResponse} is: the frontend sorts a {@code <select>} against this shape, so the
 * field names are a contract and a silent rename would reorder nothing while still returning 200.
 *
 * <h2>§4 PII: there is no field here that could carry any</h2>
 * A clause code, a label, a translation key, a count, a denominator and two booleans. No complainant
 * name, phone, email, address or account number — enforced by the TYPE, not by a serialiser
 * configuration, so a future field cannot be added by accident without a reviewer seeing it.
 *
 * <h2>What the response deliberately does NOT contain</h2>
 * No selected value, no default, no "recommended" singular, and no link or action. §5.1's "suggest,
 * highlight, do not auto-select" is strongest here because reordering a {@code <select>} moves what is
 * under the cursor — so there is nothing in this shape a client could read as an instruction to change
 * the officer's selection. The server states an order and some counts; the decision is entirely the
 * officer's.
 */
public final class ClosureClauseRecommendationResponse {

    private ClosureClauseRecommendationResponse() {
    }

    /**
     * One clause, in rank order, with the evidence for its position.
     *
     * @param clauseCode  e.g. {@code 15(1)(a)}. The key the client matches against its own option
     *                    values, so it must be the master's spelling and is never localised — a
     *                    statutory citation reads identically in all 13 locales.
     * @param label       the master's English label, carried so a client that has not loaded the clause
     *                    list separately can still render an option. The FALLBACK of record, not the
     *                    preferred display: {@code labelKey} is.
     * @param labelKey    the translation key for the label, so citizen- and officer-facing text is
     *                    never an English literal. May be null — {@code CLOSURE_CLAUSE_MASTER.labelKey}
     *                    is nullable and is null on some seeded rows.
     * @param occurrences how many complaints in the matched cohort cited this clause. The NUMERATOR. 0
     *                    when the clause has no history in the cohort.
     * @param cohortTotal how many complaints the matched cohort holds in total. The DENOMINATOR, and
     *                    not optional: §5.1's position is that "a bare recommendation with no
     *                    denominator will be distrusted, correctly". 0 when nothing was ranked.
     * @param recommended whether this clause cleared the annotation share floor. The client shows a
     *                    count ONLY when this is true, so a clause cited once in 800 cases is ordered
     *                    but never advertised. Distinct from "has a non-zero {@code occurrences}".
     * @param specificity how many of the cohort's five optional dimensions were specific rather than
     *                    wildcarded. Diagnostics for the officer-facing "why" — it is what lets the UI
     *                    say whether this ordering came from cases matching on category and entity type
     *                    or merely from the whole department.
     */
    public record ClauseRecommendation(String clauseCode,
                                       String label,
                                       String labelKey,
                                       long occurrences,
                                       long cohortTotal,
                                       boolean recommended,
                                       int specificity) {

        /** A clause with history behind it. */
        public static ClauseRecommendation ranked(ClosureClauseMaster clause, long occurrences,
                                                  long cohortTotal, boolean recommended,
                                                  int specificity) {
            return new ClauseRecommendation(clause.getClauseCode(), clause.getLabel(),
                    clause.getLabelKey(), occurrences, cohortTotal, recommended, specificity);
        }

        /**
         * A permitted clause the matched cohort never cited.
         *
         * <p>Present in the response, not omitted. A clause with no history is not a clause the role
         * may not cite — hiding it would convert a suggestion into a restriction, which belongs to
         * {@code ClosureClauseAccessService} and not to a ranking. {@code occurrences = 0} and
         * {@code recommended = false} say exactly that, and the client renders it with no annotation.
         */
        public static ClauseRecommendation unranked(ClosureClauseMaster clause) {
            return new ClauseRecommendation(clause.getClauseCode(), clause.getLabel(),
                    clause.getLabelKey(), 0L, 0L, false, 0);
        }
    }

    /**
     * The whole picker: every clause the role may cite, in the order to offer them.
     *
     * @param clauses     the permitted clauses, ranked ones first. ALWAYS the complete permitted set —
     *                    this is a permutation of it, never a subset, so a client that renders this
     *                    list verbatim offers exactly what the access service allows.
     * @param ranked      whether any ordering was applied. {@code false} means the client must render
     *                    the list as given and show no annotations — the degraded state, which covers
     *                    an unapplied migration, an empty rollup, a cohort below the sample floor, the
     *                    kill switch being off, and a query timeout. A single boolean rather than an
     *                    error code because the client's behaviour is identical in all five cases, and
     *                    an officer has no action to take in any of them.
     * @param cohortTotal the denominator behind the ordering, or 0 when nothing was ranked. Carried at
     *                    the top level as well as per clause so the UI can say "out of N comparable
     *                    cases" once rather than per option.
     */
    public record ClauseRecommendations(List<ClauseRecommendation> clauses,
                                        boolean ranked,
                                        long cohortTotal) {

        /**
         * The permitted list in the master's own order, with nothing claimed about it.
         *
         * <p>The single degraded shape. It is a SUCCESS, not an error: the picker is fully functional
         * and the officer has lost a convenience they may not have known existed. That is the whole
         * reason this feature can ship with its migration unapplied.
         */
        public static ClauseRecommendations unranked(List<ClosureClauseMaster> permitted) {
            return new ClauseRecommendations(
                    permitted.stream().map(ClauseRecommendation::unranked).toList(), false, 0L);
        }
    }
}
