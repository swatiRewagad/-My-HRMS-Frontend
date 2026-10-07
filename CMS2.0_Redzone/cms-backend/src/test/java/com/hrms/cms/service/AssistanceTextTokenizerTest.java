package com.hrms.cms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tokenizer: the four rejection rules, the SET semantics, and the read cap.
 *
 * <p>Everything asserted here is a REFUSAL or an INVARIANT. A tokenizer that let {@code "the"} through,
 * stored a 70-character string, kept account numbers, or counted a repeated word twice would still pass a
 * test that only asked "does it split on spaces" — and every one of those four failures is invisible in
 * production, because the rollup it feeds degrades to a confident wrong answer rather than to an error.
 *
 * <p>{@link AssistanceTextTokenizer#accept} is exercised DIRECTLY as well as through
 * {@link AssistanceTextTokenizer#tokenize}, deliberately. A test that could only reach the rules through
 * {@code tokenize} would pass whenever ANY rule rejected a word, so deleting the digit rule would still
 * be masked by the length rule for {@code "01"} — and the digit rule is the PII control.
 */
@DisplayName("AssistanceTextTokenizer")
class AssistanceTextTokenizerTest {

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Rule 1: the length floor is THREE, not four")
    class LengthRule {

        /**
         * The whole reason the floor is 3. MUTATION CHECK: setting MIN_TOKEN_LENGTH back to 4 — the
         * obvious choice — fails this test, which is the point of having it. These five terms are the
         * most discriminative vocabulary in the domain and a 4-character floor drops every one.
         */
        @Test
        @DisplayName("keeps ATM, UPI, EMI, KYC and NPA")
        void keepsThreeLetterDomainTerms() {
            assertThat(AssistanceTextTokenizer.tokenize("ATM UPI EMI KYC NPA"))
                    .containsExactlyInAnyOrder("atm", "upi", "emi", "kyc", "npa");
        }

        @Test
        @DisplayName("rejects anything shorter than three characters")
        void rejectsTwoCharacters() {
            assertThat(AssistanceTextTokenizer.accept("at")).isFalse();
            assertThat(AssistanceTextTokenizer.accept("a")).isFalse();
            assertThat(AssistanceTextTokenizer.accept("")).isFalse();
            assertThat(AssistanceTextTokenizer.accept(null)).isFalse();
        }

        /**
         * The column is VARCHAR(64) and the tokenizer is the only thing standing between it and a
         * truncated row. MySQL in non-strict mode would truncate silently and key a row no read could
         * ever seek; Oracle would raise and fail the refresh.
         */
        @Test
        @DisplayName("rejects anything longer than the column width, so no row can be truncated")
        void rejectsOverlongTokens() {
            String atLimit = "a".repeat(AssistanceTextTokenizer.MAX_TOKEN_LENGTH);
            String overLimit = "a".repeat(AssistanceTextTokenizer.MAX_TOKEN_LENGTH + 1);

            assertThat(AssistanceTextTokenizer.accept(atLimit)).isTrue();
            assertThat(AssistanceTextTokenizer.accept(overLimit)).isFalse();
            assertThat(AssistanceTextTokenizer.tokenize(overLimit)).isEmpty();
        }

        @Test
        @DisplayName("MAX_TOKEN_LENGTH matches the TOKEN column width in V120 / oracle V118")
        void columnWidthContract() {
            // Pinned as a constant and not read from the schema, because the schema is applied by hand
            // and the two cannot be compared at test time. If the migration's VARCHAR(64) is ever
            // changed, this is the assertion that should fail alongside it.
            assertThat(AssistanceTextTokenizer.MAX_TOKEN_LENGTH).isEqualTo(64);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Rule 2: stopwords")
    class StopwordRule {

        /**
         * MEASURED: with a 3-character floor and no three-letter stopwords, {@code "the"} appears in 32
         * of the 273 labelled complaints and ranks among the thickest tokens in the corpus, carrying a
         * 75% pointer at the majority class. This is the test that would catch its removal.
         */
        @Test
        @DisplayName("rejects the three-letter function words the lowered floor admits")
        void rejectsThreeLetterFunctionWords() {
            assertThat(AssistanceTextTokenizer.tokenize(
                    "the and not for was are has had but two one day"))
                    .isEmpty();
        }

        @Test
        @DisplayName("rejects complaint-register boilerplate that appears against every category")
        void rejectsRegisterBoilerplate() {
            assertThat(AssistanceTextTokenizer.tokenize(
                    "complaint bank account amount transaction branch customer"))
                    .isEmpty();
        }

        /**
         * The line in {@link AssistanceTextTokenizer#STOPWORDS}: this register's FIXTURE nouns are
         * deliberately NOT stopworded, because that would be tuning product code to one database's test
         * seed. Asserted so a future reader who adds them has to change a test that says why not.
         */
        @Test
        @DisplayName("does NOT stopword this register's fixture vocabulary")
        void keepsFixtureVocabularyDeliberately() {
            assertThat(AssistanceTextTokenizer.tokenize("RETLC FTWIN CEPC filing eligible detection"))
                    .containsExactlyInAnyOrder("retlc", "ftwin", "cepc", "filing", "eligible",
                            "detection");
        }

        @Test
        @DisplayName("the stopword set is immutable, so write and read cannot disagree")
        void stopwordsAreImmutable() {
            assertThat(AssistanceTextTokenizer.STOPWORDS).isNotEmpty();
            try {
                AssistanceTextTokenizer.STOPWORDS.add("withdrawal");
                org.junit.jupiter.api.Assertions.fail(
                        "STOPWORDS must be immutable: a runtime addition would make the refresh and "
                        + "the read disagree about what a stopword is, and the rollup would be keyed "
                        + "on words no read could match.");
            } catch (UnsupportedOperationException expected) {
                // The contract.
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Rule 3: ANY digit disqualifies — the PII control")
    class DigitRule {

        /**
         * The PII rule, stated as the thing it protects. An account number, a card number, an amount and
         * a reference number are all digit-bearing, and the rollup must not persist any of them.
         */
        @Test
        @DisplayName("rejects account numbers, card numbers, amounts and reference numbers")
        void rejectsNumericIdentifiers() {
            assertThat(AssistanceTextTokenizer.tokenize(
                    "account 50100123456789 card 4111111111111111 Rs.10000 ref CMS2026001234"))
                    .doesNotContain("50100123456789", "4111111111111111", "10000", "cms2026001234");
        }

        /**
         * ANY digit, not just an all-digit token — which is the mutation this test exists to catch.
         * MEASURED, {@code 01t13} and {@code 01t12} were the two THICKEST tokens in the corpus (83 and
         * 62 documents) before this rule existed; they are fragments of an ISO timestamp embedded in a
         * seeded subject line, and an all-digits-only check would have let both through.
         */
        @Test
        @DisplayName("rejects MIXED alphanumeric tokens, not only all-digit ones")
        void rejectsMixedAlphanumeric() {
            assertThat(AssistanceTextTokenizer.accept("01t13")).isFalse();
            assertThat(AssistanceTextTokenizer.accept("abc123")).isFalse();
            assertThat(AssistanceTextTokenizer.accept("x1y")).isFalse();
            assertThat(AssistanceTextTokenizer.accept("atm")).isTrue();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Normalisation, and the SET semantics the denominator depends on")
    class Normalisation {

        /**
         * Lower-casing must happen BEFORE the split, and the ordering is a silent total failure if
         * reversed: the split pattern is lower-case-only, so an upper-case letter reaching it would be
         * treated as a separator and {@code "ATM"} would vanish rather than merely failing to normalise.
         */
        @Test
        @DisplayName("lower-cases before splitting, so upper-case words survive")
        void lowerCasesBeforeSplitting() {
            assertThat(AssistanceTextTokenizer.tokenize("ATM WITHDRAWAL Failed"))
                    .containsExactlyInAnyOrder("atm", "withdrawal", "failed");
        }

        @Test
        @DisplayName("strips punctuation rather than keeping it inside a token")
        void stripsPunctuation() {
            assertThat(AssistanceTextTokenizer.tokenize("ATM-withdrawal, (unauthorised); debit!"))
                    .containsExactlyInAnyOrder("atm", "withdrawal", "unauthorised", "debit");
        }

        /**
         * ONE document contributes AT MOST ONE to any token's count. The published denominator is
         * "labelled complaints CONTAINING this token"; counting occurrences instead would make
         * TOKEN_TOTAL exceed the corpus size and turn the share into a number with no interpretation.
         *
         * <p>MUTATION CHECK: changing {@code tokenize} to return a {@code List} fails this test.
         */
        @Test
        @DisplayName("de-duplicates within one document, so a repeated word counts once")
        void deduplicatesWithinADocument() {
            Set<String> tokens = AssistanceTextTokenizer.tokenize(
                    "withdrawal withdrawal WITHDRAWAL", "withdrawal again");
            assertThat(tokens).containsExactly("withdrawal");
        }

        @Test
        @DisplayName("pools several fields and skips the ones that are null or blank")
        void poolsFieldsAndSkipsAbsentOnes() {
            // MEASURED: many complaints carry a subject with a null description, and a subject-only
            // complaint is still evidence about what words go with what category.
            assertThat(AssistanceTextTokenizer.tokenize("ATM withdrawal", null))
                    .containsExactlyInAnyOrder("atm", "withdrawal");
            assertThat(AssistanceTextTokenizer.tokenize(null, "   ", "UPI failed"))
                    .containsExactlyInAnyOrder("upi", "failed");
            assertThat(AssistanceTextTokenizer.tokenize((String[]) null)).isEmpty();
            assertThat(AssistanceTextTokenizer.tokenize()).isEmpty();
        }

        /**
         * Oracle treats the empty string AS NULL, so a tokenizer that emitted {@code ""} would violate
         * the TOKEN column's NOT NULL and fail the refresh rather than writing a useless row.
         */
        @Test
        @DisplayName("never emits a blank token, which on Oracle would violate NOT NULL")
        void neverEmitsBlank() {
            assertThat(AssistanceTextTokenizer.tokenize("!!! ,,, --- ???")).isEmpty();
            assertThat(AssistanceTextTokenizer.tokenize(" ")).isEmpty();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("The §6.2 read cap")
    class ReadCap {

        @Test
        @DisplayName("passes a small set through untouched")
        void smallSetUntouched() {
            assertThat(AssistanceTextTokenizer.capped(List.of("atm", "upi")))
                    .containsExactlyInAnyOrder("atm", "upi");
            assertThat(AssistanceTextTokenizer.capped(List.of())).isEmpty();
            assertThat(AssistanceTextTokenizer.capped(null)).isEmpty();
        }

        /**
         * A description is a TEXT column, so the token list is unbounded without this. An uncapped
         * {@code IN} list is a declared-bounded read that becomes a table scan at some size, with no
         * error to notice.
         */
        @Test
        @DisplayName("caps at MAX_QUERY_TOKENS")
        void capsAtMaxQueryTokens() {
            Set<String> many = new LinkedHashSet<>();
            for (int i = 0; i < AssistanceTextTokenizer.MAX_QUERY_TOKENS * 3; i++) {
                // Digit-free, so these are tokens the tokenizer itself would accept.
                many.add("token" + numberToLetters(i));
            }
            assertThat(many).hasSizeGreaterThan(AssistanceTextTokenizer.MAX_QUERY_TOKENS);
            assertThat(AssistanceTextTokenizer.capped(many))
                    .hasSize(AssistanceTextTokenizer.MAX_QUERY_TOKENS);
        }

        /**
         * LONGEST first, not first-encountered. Longer words are more specific, and length is a property
         * of the token itself — so the selection does not depend on where in a paragraph a word appeared,
         * and an irrelevant edit cannot change the answer.
         */
        @Test
        @DisplayName("keeps the LONGEST tokens, not the first ones encountered")
        void keepsLongestTokens() {
            Set<String> tokens = new LinkedHashSet<>();
            // Inserted SHORT-first, so a first-encountered implementation would keep the short ones.
            for (int i = 0; i < AssistanceTextTokenizer.MAX_QUERY_TOKENS; i++) {
                tokens.add("aaa" + numberToLetters(i));
            }
            String longest = "z".repeat(40);
            tokens.add(longest);

            assertThat(AssistanceTextTokenizer.capped(tokens))
                    .hasSize(AssistanceTextTokenizer.MAX_QUERY_TOKENS)
                    .contains(longest);
        }

        /**
         * Ties break ALPHABETICALLY, so the cap is a pure function of the token SET. Without that, two
         * calls with the same words in a different order could select a different 64 and the officer
         * would see a suggestion flap for no reason they could observe.
         */
        @Test
        @DisplayName("is a pure function of the set: insertion order cannot change the result")
        void insertionOrderIsIrrelevant() {
            List<String> words = new ArrayList<>();
            for (int i = 0; i < AssistanceTextTokenizer.MAX_QUERY_TOKENS + 10; i++) {
                words.add("word" + numberToLetters(i));
            }
            List<String> reversed = new ArrayList<>(words);
            java.util.Collections.reverse(reversed);

            assertThat(AssistanceTextTokenizer.capped(new LinkedHashSet<>(words)))
                    .containsExactlyInAnyOrderElementsOf(
                            AssistanceTextTokenizer.capped(new LinkedHashSet<>(reversed)));
        }

        /** Digit-free distinct suffixes, because rule 3 would reject anything numeric. */
        private static String numberToLetters(int n) {
            StringBuilder sb = new StringBuilder();
            int value = n + 1;
            while (value > 0) {
                sb.append((char) ('a' + (value % 26)));
                value /= 26;
            }
            return sb.toString();
        }
    }
}
