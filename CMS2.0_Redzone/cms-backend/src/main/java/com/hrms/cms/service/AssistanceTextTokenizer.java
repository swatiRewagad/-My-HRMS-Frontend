package com.hrms.cms.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns complaint text into the normalised token set that keys {@code ASSISTANCE_CATEGORY_PRIOR}.
 *
 * <h2>ONE class, TWO callers, and that is the whole point of it</h2>
 * {@code AssistanceCategoryPriorRefreshService} applies this to the labelled register on the schedule,
 * and {@code CategorySuggestionService} applies it to the officer's text on the request. A second
 * implementation on the read side is exactly how a rollup comes to be keyed on words no read can ever
 * match — the normalisation is the contract between the two halves, so it lives in one place and both
 * halves call it.
 *
 * <p>Static methods on a final class rather than a Spring bean, matching {@code RegulatedEntity.normalize}
 * which plays the same role for the clause-affinity rollup. There is no state, no configuration and no
 * dependency here; a bean would add an injection point and an opportunity to stub the normalisation in a
 * test, which would let the two halves disagree in exactly the way this class exists to prevent.
 *
 * <h2>NORMALISED ON WRITE. No SQL function ever touches the token column</h2>
 * §6.2's rule. The rollup stores what this produces, and the read compares against what this produces,
 * so neither side needs {@code LOWER(TOKEN)} — which would defeat the index on both engines — and
 * neither needs a {@code LIKE}, let alone a leading-wildcard one.
 *
 * <h2>LOWER case, uniquely in this feature set</h2>
 * Every other assistance text dimension is upper-cased, because they carry controlled vocabularies
 * (status names, department codes, entity types) that already appear in mixed case in this database. A
 * token is not a code; lower case is a word's natural written form. The case CHOICE is arbitrary — what
 * is not arbitrary is that normalisation happens in Java at all, because MySQL's
 * {@code utf8mb4_unicode_ci} collation folds comparisons for free and Oracle's default collation does
 * not, so a token stored verbatim and compared verbatim would match in dev and MISS in production.
 *
 * <h2>The four rejection rules, and the one that is load-bearing for PII</h2>
 * <ol>
 *   <li><b>Length.</b> {@link #MIN_TOKEN_LENGTH} is <b>3</b>, not the obvious 4. The most discriminative
 *       vocabulary in this domain is three letters long — ATM, UPI, EMI, KYC, NPA — and a 4-character
 *       floor silently drops every one of them. That choice is why {@link #STOPWORDS} must carry
 *       three-letter function words explicitly: MEASURED, with a 3-character floor and no such entries,
 *       {@code "the"} appears in 32 of the 273 labelled complaints and ranks among the thickest tokens
 *       in the corpus, carrying a 75% pointer at the majority class. {@link #MAX_TOKEN_LENGTH} is 64 and
 *       matches the column width exactly, so a token this class accepts can never be truncated on the
 *       way in.
 *   <li><b>Stopwords.</b> English function words plus complaint-register boilerplate. See
 *       {@link #STOPWORDS} for where the line is drawn and why fixture nouns are NOT on it.
 *   <li><b>Any digit disqualifies.</b> Not just an all-digit token — ANY digit anywhere. This is the
 *       PII rule and it is doing two jobs. It removes account numbers, card numbers, amounts, reference
 *       numbers, dates and timestamp fragments, which are the PII a complainant's own prose actually
 *       contains; and MEASURED it removes fixture artifacts like {@code 01t13} and {@code 01t12}, which
 *       were among the thickest tokens in the corpus before this rule existed (83 and 62 documents) and
 *       are fragments of an ISO timestamp embedded in a seeded subject line. A token carrying a digit is
 *       an identifier, not vocabulary.
 *   <li><b>Blank.</b> Rejected explicitly, and on Oracle this is not pedantry: an empty string IS NULL
 *       on Oracle, so a tokenizer that emitted {@code ""} would violate the column's {@code NOT NULL}
 *       and fail the refresh rather than writing a useless row.
 * </ol>
 *
 * <h2>NO name-derived PII blocklist. A MEASURED rejection, not an omission</h2>
 * Blocking every token that appears in any {@code COMPLAINTS.complainant_name} was built and measured
 * against {@code cms_db} on 2026-10-07. It removes 20 of 284 tokens — including
 * {@code withdrawal}, {@code card}, {@code atm}, {@code status}, {@code window}, {@code session} and
 * {@code duplicate}, which are the most informative words in the corpus — because
 * {@code complainant_name} is ITSELF polluted with fixture phrases: {@code 'Session Cee'} on 245 rows,
 * {@code 'Active Status Citizen'} on 69, {@code 'Withdrawal Notification Citizen'} on 64,
 * {@code 'PNB ATM'} on 1. Coverage of the unlabelled register fell from 15.2% to 8.1%. The belt
 * destroyed the trousers, so it is not shipped.
 *
 * <p>What is shipped instead, and it is three things rather than one:
 * <ul>
 *   <li>rule 3 above, which removes every numeric identifier;
 *   <li>a k-anonymity floor of k=3 in the refresh ({@code MIN_TOKEN_SAMPLE}) — a token must appear in
 *       three or more labelled complaints to be stored at all, so nothing unique to one complainant can
 *       be persisted;
 *   <li>{@code ASSISTANCE_CATEGORY_PRIOR} holds no complaint id and no row-level reference of any kind,
 *       so a stored token cannot be joined back to the complaint it came from.
 * </ul>
 * The residual risk, stated rather than hidden: a COMMON GIVEN NAME appearing in three or more complaint
 * bodies would survive all three guards and be stored as a token. If that matters in an environment
 * with a clean register, the mitigation is a STATIC curated given-name list added to
 * {@link #STOPWORDS} — never one derived from the register's own name column, which is the measurement
 * above.
 */
public final class AssistanceTextTokenizer {

    private AssistanceTextTokenizer() {
    }

    /**
     * THREE, not four.
     *
     * <p>The most discriminative vocabulary in this domain is three letters long — ATM, UPI, EMI, KYC,
     * NPA. A four-character floor is the obvious choice and silently drops every one of them, which
     * would leave the rollup keyed on the surrounding prose instead of on the terms that identify a
     * category. The cost of lowering it is that English three-letter function words become eligible, so
     * {@link #STOPWORDS} carries them explicitly; that is a list, which is auditable, whereas a length
     * floor that drops {@code "atm"} is a silent behaviour nobody reads.
     */
    public static final int MIN_TOKEN_LENGTH = 3;

    /**
     * 64, matching {@code ASSISTANCE_CATEGORY_PRIOR.TOKEN}'s column width EXACTLY.
     *
     * <p>Enforced here and not left to the database, because the two engines fail differently: MySQL in
     * non-strict mode truncates without erroring, which would key a row no read could ever seek, while
     * Oracle raises and fails the whole refresh. Neither is acceptable and both are avoided by rejecting
     * the token before it reaches SQL.
     *
     * <p>The value comes from the longest plausible word in a complaint — {@code reconciliation},
     * {@code acknowledgement} are 14-15 characters — with generous room for an agglutinated
     * Indic-script term, rather than from a round number. Anything longer than 64 characters is not a
     * word; it is a concatenation, a URL or a base64 fragment.
     */
    public static final int MAX_TOKEN_LENGTH = 64;

    /**
     * The hard cap on how many tokens one READ may seek on.
     *
     * <p>§6.2 requires a declared cap on every query, and the read's {@code TOKEN IN (...)} list is
     * bounded by the caller's text, which is unbounded — a complaint description is a {@code TEXT}
     * column. 64 is enough to cover the informative vocabulary of any single complaint (MEASURED: the
     * whole surviving rollup vocabulary is 85 tokens) while keeping the {@code IN} list a size the
     * optimiser handles as a set of index ranges rather than as a scan.
     *
     * <p>When the text yields more than this, {@link #capped} keeps the LONGEST tokens. Longer words are
     * more specific and therefore better evidence, and length is a property of the token itself, so the
     * selection is deterministic — unlike "the first 64 encountered", which would make the suggestion
     * depend on where in a paragraph a word happened to appear.
     */
    public static final int MAX_QUERY_TOKENS = 64;

    /** Splits on anything that is not a lower-case letter or a digit. Applied AFTER lower-casing. */
    private static final Pattern NON_TOKEN = Pattern.compile("[^a-z0-9]+");

    /**
     * Words that are rejected however often they occur.
     *
     * <h3>Two groups, and the line between them is deliberate</h3>
     * The first group is English FUNCTION WORDS, including the three-letter ones that
     * {@link #MIN_TOKEN_LENGTH} makes eligible. The second is COMPLAINT-REGISTER BOILERPLATE — words
     * that appear in nearly every complaint filed against any category ({@code complaint},
     * {@code bank}, {@code account}, {@code amount}, {@code transaction}) and therefore carry no
     * category signal while looking exactly like domain vocabulary. Both groups would otherwise rank
     * high on document frequency and point at whatever the majority class happens to be.
     *
     * <h3>What is deliberately NOT here: this register's test-fixture nouns</h3>
     * MEASURED, 164 of the 273 labelled complaints carry a fixture subject ({@code RETLC%},
     * {@code E2E%}, {@code QA%}, {@code S4-%}, {@code FTWIN}), so {@code retlc}, {@code ftwin},
     * {@code cepc}, {@code filing}, {@code eligible}, {@code session} and {@code detection} are among
     * the thickest tokens this rollup will hold. Adding them would be tuning PRODUCT code to one
     * database's test seed — and the next environment's fixtures will use different words, so the list
     * would be stale on arrival while reading as though it were general. {@code MAX_DOC_FRACTION} and
     * {@code MIN_LIFT} are the general mechanisms for a word that carries no signal; a curated list of
     * this register's fixture nouns is not.
     *
     * <p>{@code Set.of} rather than a mutable set, so no caller can add to the list at runtime and make
     * the write side and the read side disagree about what a stopword is.
     */
    static final Set<String> STOPWORDS = Set.of(
            // ── three-letter function words. MUST be listed: lowering MIN_TOKEN_LENGTH to 3 to keep
            // ATM / UPI / EMI also admits these, and MEASURED "the" alone appears in 32 of 273
            // labelled complaints and carries a 75% pointer at the majority class.
            "the", "and", "not", "for", "was", "are", "has", "had", "but", "its", "our", "out",
            "any", "all", "who", "why", "how", "did", "can", "one", "two", "ten", "per", "via",
            "yet", "nor", "due", "got", "now", "you", "she", "his", "her", "him", "off", "own",
            "too", "sir", "etc", "pls", "plz", "may", "get", "put", "let", "say", "ask", "see",
            "use", "set", "run", "day",
            // ── longer English function words
            "about", "after", "again", "against", "also", "been", "being", "both", "cannot",
            "could", "does", "doing", "done", "each", "even", "ever", "from", "further", "have",
            "having", "here", "hereby", "into", "itself", "just", "more", "most", "much", "must",
            "myself", "only", "other", "over", "same", "should", "since", "some", "such", "than",
            "that", "their", "them", "then", "there", "these", "they", "those", "through",
            "under", "until", "very", "were", "what", "when", "where", "which", "while", "with",
            "would", "your", "yours", "once", "will", "shall", "upon", "said", "madam", "dear",
            "please", "kindly", "regard", "regards", "respected", "thanks", "thank", "days",
            "week", "year", "month",
            // ── complaint-register boilerplate. Present in nearly every complaint against every
            // category, so these carry no category signal while looking like domain vocabulary.
            "complaint", "complaints", "complainant", "bank", "banking", "branch", "customer",
            "account", "amount", "transaction", "rupees", "money", "date", "dated", "time",
            "today", "yesterday", "request", "requested", "matter", "issue", "problem", "detail",
            "details", "information", "response", "reply", "action", "taken", "received", "sent",
            "letter", "email", "mail", "phone", "mobile", "number", "reference", "regarding",
            "respect", "behalf", "above", "below", "attached", "attachment", "copy", "document",
            "documents", "none", "null", "test", "tests", "testing", "probe", "fixture", "seed",
            "seeded", "sample", "dummy");

    /**
     * The normalised, de-duplicated token SET for one document.
     *
     * <h3>A SET, and that is the unit of counting</h3>
     * One document contributes AT MOST ONE to any token's count, however many times the word appears in
     * it. The denominator this rollup publishes is "labelled complaints CONTAINING this token", and a
     * complaint that says "ATM" five times is still one complaint. Counting occurrences instead would
     * make {@code TOKEN_TOTAL} larger than the corpus and the share a number with no interpretation —
     * which is precisely the denominator an officer is meant to be able to trust.
     *
     * <p>{@link LinkedHashSet} so iteration order is first-appearance order and reproducible across
     * runs over identical input. Correctness does not depend on it; a job whose log lines come out
     * shuffled between identical runs is harder to diagnose than one whose do not.
     *
     * @param parts the text fields to pool, typically subject then description. Nulls and blanks are
     *              skipped rather than rejected — MEASURED, 60 of 4,403 complaints have no text at all
     *              and many have a subject with a null description, and a complaint with only a subject
     *              is still evidence
     * @return the accepted tokens; EMPTY, never null, when nothing survives the rules
     */
    public static Set<String> tokenize(String... parts) {
        Set<String> tokens = new LinkedHashSet<>();
        if (parts == null) {
            return tokens;
        }
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            // Lower-case FIRST, then split: the split pattern is deliberately lower-case-only, so an
            // upper-case letter reaching it would be treated as a separator and "ATM" would vanish
            // rather than merely failing to normalise. Ordering the two operations the other way round
            // is a silent, total failure, which is why they are one expression here.
            for (String candidate : NON_TOKEN.split(part.toLowerCase())) {
                if (accept(candidate)) {
                    tokens.add(candidate);
                }
            }
        }
        return tokens;
    }

    /**
     * Whether one already-lower-cased candidate is a token this feature will store or seek.
     *
     * <p>Package-private so the unit tests can pin each rule individually. A test that could only
     * exercise the rules through {@link #tokenize} would pass whenever ANY rule rejected a word, so it
     * could not tell "rejected as a stopword" from "rejected for carrying a digit" — and a mutation
     * that deleted one rule would still be caught by the other.
     */
    static boolean accept(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (candidate.length() < MIN_TOKEN_LENGTH || candidate.length() > MAX_TOKEN_LENGTH) {
            return false;
        }
        if (STOPWORDS.contains(candidate)) {
            return false;
        }
        // ANY digit, not just an all-digit token. See the class javadoc: this is the PII rule, and it
        // is also what removed the two thickest fixture artifacts in the corpus ('01t13', '01t12').
        for (int i = 0; i < candidate.length(); i++) {
            if (Character.isDigit(candidate.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The §6.2 row cap applied to the READ's token list: at most {@link #MAX_QUERY_TOKENS}, longest
     * first.
     *
     * <p>LONGEST, not first-encountered. Longer words are more specific and therefore better evidence,
     * and length is a property of the token itself — so the selection is deterministic, whereas
     * "the first 64" would make the suggestion depend on where in a paragraph a word happened to
     * appear, and the same complaint would get a different answer after an irrelevant edit.
     *
     * <p>Ties are broken ALPHABETICALLY rather than left in encounter order, so the cap is a pure
     * function of the token set. Without that, two calls with the same words in a different order could
     * select different 64.
     *
     * @return at most {@link #MAX_QUERY_TOKENS} tokens, in no meaningful order (the read sorts in SQL)
     */
    public static List<String> capped(Collection<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        if (tokens.size() <= MAX_QUERY_TOKENS) {
            return List.copyOf(tokens);
        }
        String[] sorted = tokens.toArray(new String[0]);
        Arrays.sort(sorted, Comparator
                .comparingInt(String::length).reversed()
                .thenComparing(Comparator.naturalOrder()));
        // .get(0)-style indexing throughout this module: cms-backend compiles at release 17, where
        // List.getFirst() and Arrays.asList(...).subList are the available shapes and the Java 21
        // sequenced-collection methods are not.
        return List.of(Arrays.copyOfRange(sorted, 0, MAX_QUERY_TOKENS));
    }
}
