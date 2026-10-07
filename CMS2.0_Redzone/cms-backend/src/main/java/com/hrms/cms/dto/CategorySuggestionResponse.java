package com.hrms.cms.dto;

import java.util.List;

/**
 * The auto-category suggestion payload.
 *
 * <h2>This response expresses a SUGGESTION and an EXPLANATION. It cannot express a SELECTION.</h2>
 * §5.1's rule is suggest, never auto-apply, and it is enforced by the SHAPE of this record rather than by
 * a client convention: there is no {@code selected}, no {@code default}, no {@code preferred}, no
 * {@code categoryId} at the top level and no boolean a client could read as one. A frontend cannot
 * auto-select a category from this payload without inventing a field, which is the strongest form of
 * "never auto-select" available — and it matters more here than for the clause picker, because the
 * category drives routing: an auto-applied wrong category sends a complaint to the wrong office.
 *
 * <p>{@link #suggestions} is a LIST even when it holds one entry, for the same reason. A single object
 * would read as "the answer"; a ranked list reads as "candidates", which is what it is.
 *
 * <h2>Every suggestion carries its denominator, always</h2>
 * {@link Suggestion#occurrences} and {@link Suggestion#total} are both non-null on every entry, so
 * "matched 41 of 52 complaints containing 'withdrawal'" is the only statement this contract can make.
 * There is no shape here that says "category: ATM" with nothing behind it. {@link Suggestion#evidence}
 * carries the per-token breakdown so the sentence can name the words it matched on, which is the
 * difference between a number an officer can check and a number they have to take on faith.
 *
 * <h2>Always 200, and the empty signal IS a success shape</h2>
 * An unknown complaint number, blank text, text yielding too few known tokens, a cohort below the
 * floors, an absent rollup table, a disabled switch and an internal failure all yield
 * {@link #empty(String)} — a 200 with {@code available: false} and no suggestions. They are the same
 * state as far as the filer is concerned: a form that works and offers no opinion. A 4xx would
 * additionally be indistinguishable from a malformed request, since {@code GlobalExceptionHandler} maps
 * a bare {@code RuntimeException} to 400.
 *
 * <h2>No PII (§4)</h2>
 * Category ids, labels, counts and the TOKENS that matched. The tokens are the only text here and they
 * are the normalised words from the caller's OWN input — echoed back to the caller who supplied them,
 * never drawn from another complainant's text, because a token only reaches the rollup after appearing
 * in three or more complaints and after every digit-bearing candidate has been rejected. Nothing here
 * carries a name, email, phone, address or account number, and no field could hold one: there is no free
 * text field in this record and no complaint reference other than the number the caller passed.
 *
 * @param complaintNumber the complaint asked about, echoed back, or null when the caller passed raw text
 * @param available       whether a suggestion was computable at all. FALSE for every degraded state,
 *                        including the switch being off — see the class note on why they collapse
 * @param labelledTotal   how many labelled complaints the prior was mined from, or null when nothing was
 *                        computed. The figure behind "learned from N categorised complaints", which is
 *                        what lets an officer discount a suggestion drawn from a thin register rather
 *                        than having to trust it blindly
 * @param matchedTokens   how many of the caller's tokens the rollup recognised, or null. Carried because
 *                        it is the single most useful diagnostic for a quiet feature: MEASURED, 3,469 of
 *                        4,130 unlabelled complaints are silent for having fewer than two known tokens,
 *                        and this field is how a reader tells that apart from a broken read
 * @param suggestions     the ranked candidates, best first. EMPTY, never null, when nothing qualified
 */
public record CategorySuggestionResponse(
        String complaintNumber,
        boolean available,
        Long labelledTotal,
        Integer matchedTokens,
        List<Suggestion> suggestions) {

    /**
     * One candidate category, with the evidence for it.
     *
     * @param categoryId  the machine key, matching {@code complaint_categories.id} and the
     *                    {@code <option>} values the category picker already renders. The client matches
     *                    on this, so it is a contract and not a display string
     * @param name        the master's own English name, carried so a client that does not already hold
     *                    the category list can render without a second call
     * @param labelKey    the master's translation key for {@code name}, or null. Preferred by the client
     *                    over {@code name}; 13 locales are served and an English literal in a picker is
     *                    a recorded defect elsewhere in this product
     * @param occurrences the headline numerator: of the labelled complaints containing the single
     *                    strongest token that pointed here, how many were this category
     * @param total       that token's denominator. Non-null exactly when {@code occurrences} is, because
     *                    a numerator without a denominator is the thing the brief says will be
     *                    distrusted. Together these two are the "41 of 52" in the officer's sentence
     * @param confidence  the mean {@code P(category | token)} across every matched token, 0..1. Rounded
     *                    to three places on the way out — an unrounded double would render as
     *                    {@code 0.8333333333333334} and invite a client to print it
     * @param lift        {@code confidence} divided by this category's share of the labelled corpus.
     *                    Carried, not merely enforced, and that is the point: the majority category holds
     *                    74% of the labels, so a confidence of 0.8 means something very different for it
     *                    than for a category holding 2%. A client showing confidence alone would make the
     *                    majority class look authoritative everywhere
     * @param evidence    the per-token breakdown, so the UI can say which words it matched on. Capped by
     *                    the service; ordered strongest first
     */
    public record Suggestion(
            Long categoryId,
            String name,
            String labelKey,
            Long occurrences,
            Long total,
            Double confidence,
            Double lift,
            List<TokenEvidence> evidence) {
    }

    /**
     * One matched word and what it contributed.
     *
     * <p>This is the record that makes the feature auditable rather than oracular. "matched 41 of 52
     * complaints containing 'withdrawal'" is checkable by anyone with SQL access; "we are 83% confident"
     * is not.
     *
     * @param token       the normalised word, exactly as stored. Lower-case and digit-free, so it is
     *                    renderable but is not the caller's original spelling — stated here because a
     *                    UI that highlights the matched words in the user's own text must match
     *                    case-insensitively
     * @param occurrences labelled complaints containing this token that were this category
     * @param total       labelled complaints containing this token at all
     */
    public record TokenEvidence(String token, Long occurrences, Long total) {
    }

    /**
     * The degraded answer: no suggestion, and nothing for a client to render.
     *
     * <p>Returned for the switch being off, an absent or empty rollup, a query timeout, blank text, an
     * unknown complaint number, too few recognised tokens, and every candidate falling below the
     * confidence or lift floor. All of them are the SAME state as far as the filer is concerned — a form
     * that works and offers no opinion — and collapsing them here is deliberate: a client given seven
     * distinguishable failure shapes would end up rendering one of them as an error beside a control
     * that works perfectly.
     *
     * <p>{@code labelledTotal} and {@code matchedTokens} are null rather than 0, because absent and zero
     * are different claims: "mined from 0 labelled complaints" asserts something about the register,
     * and "we did not get as far as counting" does not.
     */
    public static CategorySuggestionResponse empty(String complaintNumber) {
        return new CategorySuggestionResponse(complaintNumber, false, null, null, List.of());
    }
}
