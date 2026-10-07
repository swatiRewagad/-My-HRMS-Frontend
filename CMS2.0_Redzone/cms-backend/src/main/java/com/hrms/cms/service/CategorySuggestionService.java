package com.hrms.cms.service;

import com.hrms.cms.dto.CategorySuggestionResponse;
import com.hrms.cms.dto.CategorySuggestionResponse.Suggestion;
import com.hrms.cms.dto.CategorySuggestionResponse.TokenEvidence;
import com.hrms.cms.entity.AssistanceCategoryPrior;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.repository.AssistanceCategoryPriorRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the token-to-category prior and ranks the categories a complaint's text points at.
 *
 * <h2>One bounded read, and no aggregate anywhere on this path</h2>
 * The text is tokenised by {@link AssistanceTextTokenizer} — the SAME class the refresh used on write,
 * which is the whole contract between the two halves — capped at
 * {@link AssistanceTextTokenizer#MAX_QUERY_TOKENS}, and looked up in ONE
 * {@code TOKEN IN (...)} statement that seeks the unique key once per token. §6.2's "rollups are
 * computed on a schedule, never on request" is why there is a table to read rather than a
 * {@code GROUP BY} behind the filing form.
 *
 * <h2>THE SCORE: four stated rules, in this order</h2>
 * <ol>
 *   <li><b>A category's confidence is the MEAN of {@code P(category | token)} across every MATCHED
 *       token</b>, not the sum. A sum rewards verbosity: a long description matching eight tokens would
 *       outscore a precise subject matching two for every category at once, and the resulting number
 *       would have no interpretation and no bound. The mean is a probability-shaped quantity in 0..1
 *       that can be compared against a fixed floor, which is what makes {@link #MIN_CONFIDENCE} a
 *       meaningful constant rather than one tuned to a typical text length.
 *       <p>The mean is taken over ALL matched tokens, including those that contributed NOTHING to this
 *       category. That is the load-bearing detail: a token matching 20 complaints of which 0 were
 *       category 5 is real evidence AGAINST category 5, and dividing only by the tokens that happened
 *       to mention a category would let a category supported by one word out of nine score 1.0.
 *   <li><b>It must clear {@link #MIN_CONFIDENCE}.</b>
 *   <li><b>It must clear {@link #MIN_LIFT} against its own base rate.</b> MEASURED, the majority
 *       category holds 202 of 273 labels (74%), so a category that most tokens point at most of the time
 *       is restating the prior rather than reading the text. Lift is
 *       {@code confidence / (categoryTotal / labelledTotal)}, computable from one row because the
 *       refresh denormalised both figures onto it.
 *   <li><b>Ties break on the stronger single token's denominator, then on category id.</b> Deterministic
 *       to the end, because a suggestion that reorders between two identical calls is worse than one
 *       that is merely imprecise — an officer cannot learn to expect it.
 * </ol>
 * Three of the four are FLOORS, and the ordering matters: a candidate is dropped by the strictest rule
 * it fails, so a category can be the top-scoring one and still produce no suggestion at all. Silence is
 * the correct output below the floors.
 *
 * <h2>Never 4xx, never auto-select, always a denominator</h2>
 * Every failure — blank text, an unknown complaint, too few recognised tokens, an absent rollup table,
 * a query timeout, the switch being off — returns {@link CategorySuggestionResponse#empty}. The response
 * carries no field a client could read as a selection, and every entry carries both counts.
 *
 * <h2>IDENTITY: resolved by the caller, never read from a parameter</h2>
 * This service takes no role and no user id, and that is deliberate rather than an omission. The prior
 * is role-blind — it counts what the register was categorised as, and a word's association with a
 * category is not an entitlement — so there is nothing here to filter on identity. What the controller
 * must NOT do is accept a role or a user from a query parameter in order to pass it here; the whole
 * class of defect this codebase has recorded (a legacy endpoint taking {@code role} as an untrusted
 * parameter) is avoided by this service having no such parameter to fill.
 *
 * <h2>What this can and cannot say today, measured on {@code cms_db} 2026-10-07</h2>
 * <ul>
 *   <li>COVERAGE: 628 of the 4,130 unlabelled complaints (15.2%) would receive a suggestion. 3,469 are
 *       silent for carrying fewer than {@link #MIN_MATCHED_TOKENS} known tokens; 33 fall below the
 *       confidence or lift floor. <b>Five complaints in six get nothing</b>, which is measured and
 *       expected rather than broken.
 *   <li>SELF-CHECK on the 273 labelled complaints (not held out, so an upper bound and not an accuracy
 *       figure): 212 top-1 correct, 10 wrong, 51 silent — 95.5% precision where it spoke.
 *   <li>599 of the 628 suggestions point at ONE category. The prior is learned from a corpus that is 74%
 *       one class and 60% test-fixture text.
 *   <li><b>SOME SUGGESTIONS WILL BE CONFIDENTLY WRONG ABOUT BANKING WHILE BEING RIGHT ABOUT THE
 *       REGISTER.</b> {@code config/DemoDataSeeder.java:72} mislabels nine of its ten seeded subjects,
 *       so the token {@code atm} points at category 5 (Loan / Advances) on 11 of its 21 labelled
 *       documents. This service reports that with a true denominator attached, which is the honest
 *       behaviour; the fix is one line in the seeder and not a correction here.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategorySuggestionService {

    /**
     * Minimum RECOGNISED tokens before any suggestion is made.
     *
     * <p>TWO. One known word is an anecdote rather than a reading of the text: a complaint whose only
     * recognised token is {@code card} would be classified by that single word, and the mean in rule 1
     * would collapse to that one token's share — so {@link #MIN_CONFIDENCE} would be measuring one
     * co-occurrence rather than an agreement between several.
     *
     * <p>MEASURED, this is the floor that produces almost all of the silence: 3,469 of the 4,130
     * unlabelled complaints fail it, against 33 that fail the confidence and lift floors combined. It is
     * kept at 2 and not raised, because raising it to 3 is the difference between a feature that speaks
     * on one complaint in six and one that barely speaks at all — and it is not lowered to 1, because a
     * single-word classification of a routing-relevant field is exactly the confident-and-wrong output
     * this feature must not produce.
     */
    static final int MIN_MATCHED_TOKENS = 2;

    /**
     * Minimum mean {@code P(category | token)} for a category to be suggested at all.
     *
     * <p>0.35. Below a third, the matched words are pointing somewhere else more often than here, and a
     * suggestion is a claim that the text is ABOUT this category rather than that it mentions it.
     *
     * <p>Not 0.5, which would be the natural reading of "more likely than not". With 10 categories and
     * a corpus of 273, a genuine signal spread over three plausible categories produces means in the
     * 0.3-0.4 range, and a 0.5 floor would keep only the majority class — which is the one suggestion
     * that needs no feature to produce. The lift floor below is what stops 0.35 from being permissive.
     */
    static final double MIN_CONFIDENCE = 0.35d;

    /**
     * Minimum lift over the category's own base rate.
     *
     * <p>1.0 — a suggestion must at least BEAT guessing. This is the floor that stops the feature from
     * naming the majority class, and it is the reason {@code CATEGORY_TOTAL} and {@code LABELLED_TOTAL}
     * are denormalised onto every rollup row.
     *
     * <p>MEASURED, it matters a great deal: category 1 holds 74% of the labels, so without this floor a
     * category-1 suggestion at confidence 0.4 would pass {@link #MIN_CONFIDENCE} while being
     * substantially worse than guessing. With it, raw coverage falls from roughly 15.4% to 15.2% on this
     * register (and to 8.1% when a name-derived token blocklist was also applied, which is why that
     * blocklist was rejected — see {@link AssistanceTextTokenizer}). The cost is small and the thing it
     * buys is that no suggestion this feature makes is worse than the base rate.
     *
     * <p>Exactly 1.0 and not 1.2. A higher floor would be a judgement that a marginal improvement over
     * guessing is not worth showing, and that judgement belongs to the officer who can see the
     * denominator — which is the entire reason the denominator is on the wire.
     */
    static final double MIN_LIFT = 1.0d;

    /**
     * How many categories one response may carry.
     *
     * <p>THREE. The picker holds 10 options; three candidates is enough for the UI to highlight a short
     * head without the "suggestion" becoming a reordering of the whole list, which is a different
     * feature with a different consent question. §6.2 wants a declared bound on the output as well as on
     * the query.
     */
    static final int MAX_SUGGESTIONS = 3;

    /**
     * How many matched tokens are reported as evidence per suggestion.
     *
     * <p>FIVE, strongest first. The UI sentence is "matched 41 of 52 complaints containing
     * 'withdrawal'" — one or two words long. Five is enough for a tooltip or an expanded panel without
     * putting a 64-item list on a wire that is read on a filing form.
     */
    static final int MAX_EVIDENCE_PER_SUGGESTION = 5;

    private final AssistanceCategoryPriorRepository priorRepository;
    private final ComplaintCategoryRepository categoryRepository;

    /**
     * The §6.2 GLOBAL kill switch, shared with the rail and the sibling features.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null.
     */
    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * This feature's OWN §6.2 kill switch, defaulting to {@code false} IN CODE.
     *
     * <p>The same key the refresh job reads and the same spelling as the route, so an operator cannot
     * turn off the display while a scheduled text pass carries on. Defaults to the safe state because a
     * feature that degrades SILENTLY cannot be observed failing: it is turned on per environment by
     * someone who has looked at it there, not by a default.
     */
    @Value("${cms.assistance.category-suggestion.enabled:false}")
    private boolean categorySuggestionEnabled;

    /** Whether the feature is switched on. Both keys, because the service enforces both. */
    public boolean enabled() {
        return assistanceEnabled && categorySuggestionEnabled;
    }

    /**
     * Ranks the categories this text points at.
     *
     * <h3>Never throws, and never returns null</h3>
     * Every failure path — an absent rollup table, a query timeout, a category master that no longer
     * holds a suggested id — is absorbed into {@link CategorySuggestionResponse#empty}. The caller is a
     * filing form that must stay usable, and a feature whose contract is to be quiet when it cannot
     * answer has to be quiet when it FAILS too, or the two states become distinguishable to a client
     * that will then render one of them as an error.
     *
     * @param complaintNumber echoed into the response for correlation; may be null. NOT used to look
     *                        anything up here — the controller has already resolved it to text, so this
     *                        service issues no complaint read at all
     * @param parts           the text fields to pool, typically subject then description. Nulls and
     *                        blanks are skipped
     */
    public CategorySuggestionResponse suggest(String complaintNumber, String... parts) {
        if (!enabled()) {
            return CategorySuggestionResponse.empty(complaintNumber);
        }
        try {
            return rank(complaintNumber, AssistanceTextTokenizer.capped(
                    AssistanceTextTokenizer.tokenize(parts)));
        } catch (Exception e) {
            // Includes the unapplied-migration case: the rollup table does not exist, the read throws,
            // and the correct outcome is a working form with no hint rather than a 500.
            log.warn("Category suggestion read failed for {}: {}", complaintNumber, e.toString());
            return CategorySuggestionResponse.empty(complaintNumber);
        }
    }

    /**
     * The four ranking rules, applied to one already-tokenised, already-capped token list.
     *
     * <p>Separate from {@link #suggest} so a unit test can drive the scoring with an exact token list
     * and no tokenizer in the way. Package-private rather than private for the same reason — a test that
     * could only reach this through {@link #suggest} would be asserting about the tokenizer and the
     * scorer at once, and a mutation in either would be attributable to neither.
     */
    CategorySuggestionResponse rank(String complaintNumber, List<String> tokens) {
        if (tokens.isEmpty()) {
            return CategorySuggestionResponse.empty(complaintNumber);
        }

        List<AssistanceCategoryPrior> rows = priorRepository.findByTokens(
                tokens, PageRequest.of(0, AssistanceCategoryPrior.MAX_PRIOR_ROWS));
        if (rows.isEmpty()) {
            return CategorySuggestionResponse.empty(complaintNumber);
        }
        if (rows.size() >= AssistanceCategoryPrior.MAX_PRIOR_ROWS) {
            // The cap was REACHED, so the distribution may be truncated and the means computed below
            // would be over a subset of the evidence. Warned rather than silently served, because a
            // truncated mean is wrong in an unbounded direction and looks exactly like a correct one.
            log.warn("Category suggestion read hit its {}-row cap; the suggestion is computed from a "
                    + "truncated distribution. Raise AssistanceCategoryPrior.MAX_PRIOR_ROWS.",
                    AssistanceCategoryPrior.MAX_PRIOR_ROWS);
        }

        // How many of the caller's tokens the rollup actually RECOGNISED — the divisor in rule 1, and
        // the field that distinguishes "quiet because the text had no known words" from "quiet because
        // the read failed". Counted from the rows returned rather than from the tokens asked for.
        Set<String> matched = new java.util.LinkedHashSet<>();
        long labelledTotal = 0L;
        for (AssistanceCategoryPrior row : rows) {
            matched.add(row.getToken());
            // Every row of one refresh pass carries the same corpus size, so the last one read is as
            // good as any. Taken from a row rather than from a second query precisely so the base rate
            // comes from the same pass as the counts it is compared against.
            if (row.getLabelledTotal() != null) {
                labelledTotal = row.getLabelledTotal();
            }
        }
        if (matched.size() < MIN_MATCHED_TOKENS) {
            // The dominant silence. MEASURED: 3,469 of 4,130 unlabelled complaints land here.
            return CategorySuggestionResponse.empty(complaintNumber);
        }

        Map<Long, Candidate> candidates = tally(rows);
        List<Suggestion> suggestions = qualify(candidates, matched.size());
        if (suggestions.isEmpty()) {
            return CategorySuggestionResponse.empty(complaintNumber);
        }

        return new CategorySuggestionResponse(complaintNumber, true,
                labelledTotal > 0 ? labelledTotal : null, matched.size(), suggestions);
    }

    /**
     * Accumulates each category's evidence across every matched token.
     *
     * <p>{@link LinkedHashMap} so a tie that survives the comparator still resolves the same way between
     * two identical calls — the rows arrive in {@code (token, categoryKey)} order from the index, so the
     * insertion order is itself deterministic.
     */
    private Map<Long, Candidate> tally(List<AssistanceCategoryPrior> rows) {
        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        for (AssistanceCategoryPrior row : rows) {
            if (row.getCategoryKey() == null || row.getTokenTotal() == null
                    || row.getTokenTotal() <= 0 || row.getOccurrences() == null) {
                // A row that cannot produce a share. Unreachable through the schema (all four columns
                // are NOT NULL and the refresh never writes a zero denominator) and skipped rather than
                // trusted, because the alternative is a division by zero inside a request path whose
                // contract is never to fail.
                continue;
            }
            candidates.computeIfAbsent(row.getCategoryKey(), k -> new Candidate()).add(row);
        }
        return candidates;
    }

    /**
     * Applies the confidence and lift floors, sorts, caps, and attaches the category master's labels.
     *
     * <p>The master read is {@code findAllById} over the SURVIVING category ids only — at most
     * {@link #MAX_SUGGESTIONS} of them — so it is a bounded primary-key read and not a scan. Done after
     * the floors rather than before, so a candidate that was going to be dropped never costs a lookup.
     *
     * <p>A category the master no longer holds is DROPPED, silently. The rollup is derived data and can
     * outlive a retired category by up to one refresh interval; suggesting an id the picker does not
     * render would produce a highlight pointing at nothing. This is the same resolution the clause
     * recommendation uses for a clause code the master does not hold, and it is why the rollup carries
     * no foreign key — the read is the integrity check.
     */
    private List<Suggestion> qualify(Map<Long, Candidate> candidates, int matchedTokens) {
        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<Long, Candidate> entry : candidates.entrySet()) {
            Candidate candidate = entry.getValue();
            // Rule 1: the MEAN over ALL matched tokens, not over the tokens that mentioned this
            // category. See the class javadoc — dividing by candidate.rows.size() here would let a
            // category supported by one word out of nine score 1.0, which is the single most important
            // line in this file to get right.
            double confidence = candidate.shareSum() / matchedTokens;
            if (confidence < MIN_CONFIDENCE) {
                continue;
            }
            double baseRate = candidate.baseRate();
            // A base rate of 0 means the category has no complaints, which cannot be true of a category
            // that appears in the rollup. Treated as unqualified rather than as infinite lift: dividing
            // by it would yield Infinity and pass every floor.
            if (baseRate <= 0d) {
                continue;
            }
            double lift = confidence / baseRate;
            if (lift < MIN_LIFT) {
                continue;
            }
            scored.add(new Scored(entry.getKey(), candidate, confidence, lift));
        }
        if (scored.isEmpty()) {
            return List.of();
        }

        // Rule 4. Confidence first, then the stronger single token's DENOMINATOR — a category evidenced
        // by a word seen in 52 complaints outranks one evidenced by a word seen in 4 at the same
        // confidence — then the id, so the order is total and no two calls can disagree.
        scored.sort(Comparator
                .comparingDouble((Scored s) -> s.confidence).reversed()
                .thenComparing(Comparator.comparingLong((Scored s) -> s.candidate.strongestTotal())
                        .reversed())
                .thenComparingLong(s -> s.categoryId));

        List<Scored> head = scored.subList(0, Math.min(MAX_SUGGESTIONS, scored.size()));
        List<Long> ids = new ArrayList<>(head.size());
        for (Scored s : head) {
            ids.add(s.categoryId);
        }
        Map<Long, ComplaintCategory> master = new HashMap<>();
        for (ComplaintCategory category : categoryRepository.findAllById(ids)) {
            master.put(category.getId(), category);
        }

        List<Suggestion> out = new ArrayList<>(head.size());
        for (Scored s : head) {
            ComplaintCategory category = master.get(s.categoryId);
            if (category == null) {
                continue;
            }
            AssistanceCategoryPrior strongest = s.candidate.strongest();
            out.add(new Suggestion(
                    s.categoryId,
                    category.getName(),
                    category.getLabelKey(),
                    strongest.getOccurrences(),
                    strongest.getTokenTotal(),
                    round(s.confidence),
                    round(s.lift),
                    s.candidate.evidence()));
        }
        return List.copyOf(out);
    }

    /**
     * Three decimal places.
     *
     * <p>An unrounded double renders as {@code 0.8333333333333334} in JSON, which invites a client to
     * print it and makes a derived statistic look like a measurement. Three places is more precision
     * than a percentage needs and less than the arithmetic would volunteer.
     */
    private static Double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    /** One category's accumulated evidence across the matched tokens. */
    private static final class Candidate {

        private final List<AssistanceCategoryPrior> rows = new ArrayList<>();
        private double shareSum;

        void add(AssistanceCategoryPrior row) {
            rows.add(row);
            shareSum += row.share();
        }

        /** The sum of {@code P(category | token)}; the MEAN's numerator. Divided by MATCHED tokens. */
        double shareSum() {
            return shareSum;
        }

        /**
         * This category's share of the labelled corpus.
         *
         * <p>Read from the first row, because every row of one refresh pass carries the same pair of
         * figures for the same category. Taken from a row rather than from a second query so the base
         * rate comes from the same pass as the counts it is compared against.
         */
        double baseRate() {
            return rows.isEmpty() ? 0d : rows.get(0).baseRate();
        }

        /** The single strongest token for this category: highest share, then largest denominator. */
        AssistanceCategoryPrior strongest() {
            AssistanceCategoryPrior best = rows.get(0);
            for (AssistanceCategoryPrior row : rows) {
                if (row.share() > best.share()
                        || (row.share() == best.share()
                            && row.getTokenTotal() > best.getTokenTotal())) {
                    best = row;
                }
            }
            return best;
        }

        long strongestTotal() {
            Long total = strongest().getTokenTotal();
            return total == null ? 0L : total;
        }

        /** The per-token breakdown, strongest first, capped. */
        List<TokenEvidence> evidence() {
            List<AssistanceCategoryPrior> sorted = new ArrayList<>(rows);
            sorted.sort(Comparator
                    .comparingDouble(AssistanceCategoryPrior::share).reversed()
                    .thenComparing(Comparator.comparingLong(
                            (AssistanceCategoryPrior r) -> r.getTokenTotal()).reversed())
                    .thenComparing(AssistanceCategoryPrior::getToken));
            List<TokenEvidence> out = new ArrayList<>();
            for (AssistanceCategoryPrior row : sorted) {
                if (out.size() >= MAX_EVIDENCE_PER_SUGGESTION) {
                    break;
                }
                out.add(new TokenEvidence(row.getToken(), row.getOccurrences(), row.getTokenTotal()));
            }
            return List.copyOf(out);
        }
    }

    /** A candidate that cleared both floors, with the two figures the sort reads. */
    private static final class Scored {
        private final long categoryId;
        private final Candidate candidate;
        private final double confidence;
        private final double lift;

        Scored(long categoryId, Candidate candidate, double confidence, double lift) {
            this.categoryId = categoryId;
            this.candidate = candidate;
            this.confidence = confidence;
            this.lift = lift;
        }
    }
}
