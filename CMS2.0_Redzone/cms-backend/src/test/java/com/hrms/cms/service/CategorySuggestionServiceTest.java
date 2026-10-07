package com.hrms.cms.service;

import com.hrms.cms.dto.CategorySuggestionResponse;
import com.hrms.cms.entity.AssistanceCategoryPrior;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.repository.AssistanceCategoryPriorRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The suggestion read: the four ranking rules, the three floors, and the refusals.
 *
 * <h2>Why the LIFT tests are the most important ones in this file</h2>
 * MEASURED on {@code cms_db}, the majority category holds 202 of 273 labels — 74%. A scorer with no lift
 * floor would suggest it for almost every complaint, at a confidence that looked respectable, with a
 * true denominator attached. That output is worse than silence, because it is confident, checkable and
 * no better than guessing. {@link LiftFloor} is the only thing standing between this feature and that
 * behaviour, so it is mutation-checked twice: once that the majority class IS refused, and once that an
 * identical confidence for a MINORITY category is accepted.
 *
 * <h2>And why the MEAN test is the second most important</h2>
 * Rule 1 takes the mean over ALL matched tokens, including those that contributed nothing to the
 * category being scored. {@link #meanIsOverAllMatchedTokens} is the test that catches the natural
 * mistake — dividing by the number of rows the category happened to have — which would let a category
 * supported by one word out of nine score 1.0 and clear every floor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CategorySuggestionService")
class CategorySuggestionServiceTest {

    /** Category 1 on the real register: 202 of 273 labels, a 74% base rate. */
    private static final long MAJORITY = 1L;
    /** Category 5 on the real register: 20 of 273 labels, a 7.3% base rate. */
    private static final long MINORITY = 5L;
    private static final long CORPUS = 273L;
    private static final long MAJORITY_SIZE = 202L;
    private static final long MINORITY_SIZE = 20L;

    @Mock private AssistanceCategoryPriorRepository priorRepository;
    @Mock private ComplaintCategoryRepository categoryRepository;

    private CategorySuggestionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")   // the Answer has to cast getArgument(0) to Iterable<Long>
    void setUp() {
        service = new CategorySuggestionService(priorRepository, categoryRepository);
        // Both are @Value FIELD-injected (the class is @RequiredArgsConstructor and Lombok does not
        // copy @Value onto generated constructor parameters), so a unit test has to set them here.
        ReflectionTestUtils.setField(service, "assistanceEnabled", true);
        ReflectionTestUtils.setField(service, "categorySuggestionEnabled", true);

        when(priorRepository.findByTokens(anyCollection(), any(Pageable.class)))
                .thenReturn(List.of());
        // The master resolves whatever the scorer asks for, so a test that is about the SCORE is not
        // also about the master. The one test that is about the master overrides this.
        when(categoryRepository.findAllById(any())).thenAnswer(inv -> {
            // NULL-GUARDED, and not defensively. A test that RE-stubs this with
            // when(categoryRepository.findAllById(any())) invokes the method to build the matcher, and
            // that invocation reaches THIS answer with a null argument — so an unguarded cast here
            // fails two tests that are about something else entirely. doReturn().when() would avoid the
            // re-entry; the guard is kept because it makes the trap visible at the place it bites.
            Iterable<Long> requested = (Iterable<Long>) inv.getArgument(0);
            List<ComplaintCategory> out = new ArrayList<>();
            if (requested == null) {
                return out;
            }
            for (Long id : requested) {
                out.add(ComplaintCategory.builder()
                        .id(id).name("Category " + id).labelKey("category." + id).build());
            }
            return out;
        });
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────────────────────────

    /** One rollup row: "of {@code total} complaints containing {@code token}, {@code occ} were {@code cat}". */
    private static AssistanceCategoryPrior prior(String token, long cat, long occ, long total,
                                                 long catSize) {
        return AssistanceCategoryPrior.builder()
                .token(token).categoryKey(cat)
                .occurrences(occ).tokenTotal(total)
                .categoryTotal(catSize).labelledTotal(CORPUS)
                .refreshedAt(LocalDateTime.now())
                .build();
    }

    private void rollupHolds(AssistanceCategoryPrior... rows) {
        when(priorRepository.findByTokens(anyCollection(), any(Pageable.class)))
                .thenReturn(Arrays.asList(rows));
    }

    private CategorySuggestionResponse rank(String... tokens) {
        return service.rank("CMP-1", List.of(tokens));
    }

    private static CategorySuggestionResponse.Suggestion suggestionFor(
            CategorySuggestionResponse response, long categoryId) {
        return response.suggestions().stream()
                .filter(s -> s.categoryId() == categoryId).findFirst().orElse(null);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("The §6.2 kill switches")
    class KillSwitches {

        /**
         * Off means off BEFORE the read. §6.2 requires the switch to stop the queries and not merely the
         * display, so a disabled feature must not keep hitting the rollup.
         */
        @Test
        @DisplayName("the global switch off returns the empty signal and issues NO query")
        void globalSwitchOff() {
            ReflectionTestUtils.setField(service, "assistanceEnabled", false);

            CategorySuggestionResponse response = service.suggest("CMP-1", "ATM withdrawal failed");

            assertThat(response.available()).isFalse();
            assertThat(response.suggestions()).isEmpty();
            verifyNoInteractions(priorRepository, categoryRepository);
        }

        @Test
        @DisplayName("this feature's own switch off returns the empty signal and issues NO query")
        void featureSwitchOff() {
            ReflectionTestUtils.setField(service, "categorySuggestionEnabled", false);

            assertThat(service.suggest("CMP-1", "ATM withdrawal failed").available()).isFalse();
            verifyNoInteractions(priorRepository, categoryRepository);
            assertThat(service.enabled()).isFalse();
        }

        @Test
        @DisplayName("enabled() is the CONJUNCTION of both keys, which is what /status reports")
        void enabledIsTheConjunction() {
            assertThat(service.enabled()).isTrue();
            ReflectionTestUtils.setField(service, "assistanceEnabled", false);
            assertThat(service.enabled()).isFalse();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Rule 1: confidence is the MEAN over ALL matched tokens")
    class MeanRule {

        /**
         * THE most subtle line in the service. Two tokens are matched. {@code foreclosure} points at the
         * minority category on 20 of 20 complaints — a share of 1.0 — and {@code withdrawal} has no row
         * for that category at all, because none of the complaints containing it were classified there.
         *
         * <p>The mean must be {@code 1.0 / 2 = 0.5}, not {@code 1.0 / 1 = 1.0}. A token matching 20
         * complaints of which 0 were this category is real evidence AGAINST it.
         *
         * <p>MUTATION CHECK: changing the divisor from {@code matchedTokens} to
         * {@code candidate.rows.size()} — which reads perfectly naturally — makes the confidence here
         * 1.0 and this test fails. Nothing else in the suite catches it.
         */
        @Test
        @DisplayName("a token that contributed NOTHING still counts in the divisor")
        void meanIsOverAllMatchedTokens() {
            rollupHolds(
                    prior("foreclosure", MINORITY, 20, 20, MINORITY_SIZE),
                    prior("withdrawal", MAJORITY, 90, 100, MAJORITY_SIZE));

            CategorySuggestionResponse response = rank("foreclosure", "withdrawal");
            CategorySuggestionResponse.Suggestion minority = suggestionFor(response, MINORITY);

            assertThat(response.matchedTokens()).isEqualTo(2);
            assertThat(minority).isNotNull();
            assertThat(minority.confidence()).isEqualTo(0.5d);
        }

        /** The response reports how many tokens were recognised — the key diagnostic for a quiet read. */
        @Test
        @DisplayName("reports the number of RECOGNISED tokens, not the number asked about")
        void reportsMatchedTokenCount() {
            rollupHolds(
                    prior("foreclosure", MINORITY, 18, 20, MINORITY_SIZE),
                    prior("penalty", MINORITY, 14, 20, MINORITY_SIZE));

            // Four tokens asked about, two recognised.
            CategorySuggestionResponse response = rank("foreclosure", "penalty", "unknownword",
                    "alsounknown");

            assertThat(response.matchedTokens()).isEqualTo(2);
            assertThat(response.labelledTotal()).isEqualTo(CORPUS);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Floor 1: MIN_MATCHED_TOKENS — one known word is an anecdote")
    class MatchedTokenFloor {

        /**
         * A single recognised word would classify the complaint by itself, and the mean in rule 1 would
         * collapse to that one token's share — so the confidence floor would be measuring one
         * co-occurrence rather than an agreement between several.
         *
         * <p>MEASURED, this floor produces almost all of the feature's silence: 3,469 of the 4,130
         * unlabelled complaints fail it, against 33 that fail the confidence and lift floors combined.
         *
         * <p>MUTATION CHECK: deleting the check makes this a confident suggestion at 1.0.
         */
        @Test
        @DisplayName("one recognised token yields NO suggestion, however strong it is")
        void oneMatchedTokenIsRefused() {
            rollupHolds(prior("foreclosure", MINORITY, 20, 20, MINORITY_SIZE));

            CategorySuggestionResponse response = rank("foreclosure", "unknownword");

            assertThat(response.available()).isFalse();
            assertThat(response.suggestions()).isEmpty();
            assertThat(response.matchedTokens()).isNull();
        }

        @Test
        @DisplayName("MIN_MATCHED_TOKENS is 2 — not 1, which would be a single-word classification")
        void floorIsTwo() {
            assertThat(CategorySuggestionService.MIN_MATCHED_TOKENS).isEqualTo(2);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Floor 2: MIN_CONFIDENCE")
    class ConfidenceFloor {

        /**
         * Below a third, the matched words point somewhere else more often than here, and a suggestion
         * is a claim that the text is ABOUT this category rather than that it mentions it.
         */
        @Test
        @DisplayName("a category below the confidence floor is not suggested")
        void belowConfidenceIsRefused() {
            // Each token points at the minority category on 3 of 10 complaints: mean 0.3 < 0.35.
            rollupHolds(
                    prior("foreclosure", MINORITY, 3, 10, MINORITY_SIZE),
                    prior("penalty", MINORITY, 3, 10, MINORITY_SIZE));

            assertThat(rank("foreclosure", "penalty").available()).isFalse();
        }

        @Test
        @DisplayName("a category above it is suggested")
        void aboveConfidenceIsAccepted() {
            rollupHolds(
                    prior("foreclosure", MINORITY, 4, 10, MINORITY_SIZE),
                    prior("penalty", MINORITY, 4, 10, MINORITY_SIZE));

            CategorySuggestionResponse response = rank("foreclosure", "penalty");

            assertThat(response.available()).isTrue();
            assertThat(suggestionFor(response, MINORITY).confidence()).isEqualTo(0.4d);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Floor 3: MIN_LIFT — the floor that stops the feature naming the majority class")
    class LiftFloor {

        /**
         * The single most important refusal in the feature. The majority category holds 202 of 273
         * labels, so its base rate is 0.74. A confidence of 0.5 clears {@link
         * CategorySuggestionService#MIN_CONFIDENCE} comfortably and is nonetheless substantially WORSE
         * than guessing — lift 0.676.
         *
         * <p>MUTATION CHECK: deleting the lift floor makes this a suggestion with a true denominator
         * attached, which is the confidently-useless output this whole feature exists to avoid. Nothing
         * else in the suite catches it.
         */
        @Test
        @DisplayName("the 74% majority category is REFUSED at a confidence that clears every other floor")
        void majorityClassIsRefused() {
            rollupHolds(
                    prior("withdrawal", MAJORITY, 50, 100, MAJORITY_SIZE),
                    prior("dispute", MAJORITY, 50, 100, MAJORITY_SIZE));

            CategorySuggestionResponse response = rank("withdrawal", "dispute");

            // Confidence 0.5 — well above MIN_CONFIDENCE — but lift 0.5/0.74 = 0.676.
            assertThat(response.available()).isFalse();
            assertThat(response.suggestions()).isEmpty();
        }

        /**
         * The same confidence for a MINORITY category is accepted, which is what makes the floor a
         * statement about evidence rather than a blanket threshold. 0.5 against a 7.3% base rate is a
         * lift of 6.8.
         */
        @Test
        @DisplayName("the SAME confidence for a 7.3% category is accepted — lift, not confidence, decides")
        void minorityClassAtTheSameConfidenceIsAccepted() {
            rollupHolds(
                    prior("foreclosure", MINORITY, 50, 100, MINORITY_SIZE),
                    prior("penalty", MINORITY, 50, 100, MINORITY_SIZE));

            CategorySuggestionResponse response = rank("foreclosure", "penalty");
            CategorySuggestionResponse.Suggestion suggestion = suggestionFor(response, MINORITY);

            assertThat(response.available()).isTrue();
            assertThat(suggestion.confidence()).isEqualTo(0.5d);
            assertThat(suggestion.lift()).isEqualTo(6.825d);  // 0.5 / (20/273)
        }

        /**
         * The majority category IS suggestable — when the text genuinely points at it. 0.95 against a
         * 0.74 base rate is a lift of 1.28. The floor refuses weak majority-class suggestions, not the
         * category.
         */
        @Test
        @DisplayName("the majority category is still suggestable when the evidence is strong enough")
        void strongMajorityEvidenceIsAccepted() {
            rollupHolds(
                    prior("withdrawal", MAJORITY, 95, 100, MAJORITY_SIZE),
                    prior("dispense", MAJORITY, 95, 100, MAJORITY_SIZE));

            assertThat(rank("withdrawal", "dispense").available()).isTrue();
        }

        /** A zero base rate would divide to Infinity and clear every floor. Treated as unqualified. */
        @Test
        @DisplayName("a zero base rate is unqualified, never infinite lift")
        void zeroBaseRateIsUnqualified() {
            AssistanceCategoryPrior broken = prior("foreclosure", MINORITY, 20, 20, 0L);
            AssistanceCategoryPrior broken2 = prior("penalty", MINORITY, 20, 20, 0L);

            rollupHolds(broken, broken2);

            assertThat(rank("foreclosure", "penalty").available()).isFalse();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("The output contract")
    class OutputContract {

        /**
         * The brief's position: "a bare recommendation with no denominator will be distrusted,
         * correctly". Every entry must carry both counts and the evidence that produced them.
         */
        @Test
        @DisplayName("every suggestion carries its numerator, its denominator and its evidence")
        void everySuggestionCarriesItsDenominator() {
            rollupHolds(
                    prior("foreclosure", MINORITY, 41, 52, MINORITY_SIZE),
                    prior("penalty", MINORITY, 20, 40, MINORITY_SIZE));

            CategorySuggestionResponse.Suggestion suggestion =
                    suggestionFor(rank("foreclosure", "penalty"), MINORITY);

            assertThat(suggestion).isNotNull();
            // The strongest single token: 41 of 52 is a higher share than 20 of 40.
            assertThat(suggestion.occurrences()).isEqualTo(41L);
            assertThat(suggestion.total()).isEqualTo(52L);
            assertThat(suggestion.evidence()).isNotEmpty();
            assertThat(suggestion.evidence()).allSatisfy(e -> {
                assertThat(e.occurrences()).isNotNull();
                assertThat(e.total()).isNotNull();
                assertThat(e.token()).isNotBlank();
            });
            // "matched 41 of 52 complaints containing 'foreclosure'" — the sentence the UI renders.
            assertThat(suggestion.evidence().get(0).token()).isEqualTo("foreclosure");
            assertThat(suggestion.evidence().get(0).occurrences()).isEqualTo(41L);
            assertThat(suggestion.evidence().get(0).total()).isEqualTo(52L);
        }

        /**
         * §5.1: suggest, never auto-apply. Enforced by the SHAPE of the record rather than by a client
         * convention — there must be no component a client could read as a selection. This matters more
         * here than for the clause picker, because the category drives routing: an auto-applied wrong
         * category sends a complaint to the wrong office.
         *
         * <p>MUTATION CHECK: adding a {@code selected} or {@code preferred} component to either record
         * fails this test, which is the only place that decision would be visible.
         */
        @Test
        @DisplayName("no field a client could read as an auto-selection")
        void cannotExpressASelection() {
            List<String> forbidden = List.of("selected", "default", "preferred", "apply", "autoapply",
                    "chosen", "categoryid");

            for (RecordComponent component : CategorySuggestionResponse.class.getRecordComponents()) {
                assertThat(forbidden).doesNotContain(component.getName().toLowerCase());
            }
            // categoryId is permitted ON a Suggestion — that is which candidate it is — and forbidden at
            // the TOP level, where it would be "the answer".
            assertThat(CategorySuggestionResponse.Suggestion.class.getRecordComponents())
                    .extracting(c -> c.getName().toLowerCase())
                    .doesNotContain("selected", "default", "preferred", "apply", "chosen");
        }

        /**
         * FIVE categories qualify and only THREE may be returned. The picker holds 10 options; three
         * candidates is enough for a UI to highlight a short head without the "suggestion" becoming a
         * reordering of the whole list, which is a different feature with a different consent question.
         */
        @Test
        @DisplayName("caps the response at MAX_SUGGESTIONS")
        void capsAtMaxSuggestions() {
            // Five categories, each at confidence 0.5 against a 10/273 base rate — lift 13.6, so all
            // five clear every floor and only the cap can reduce them.
            List<AssistanceCategoryPrior> rows = new ArrayList<>();
            for (long cat = 2L; cat <= 6L; cat++) {
                rows.add(prior("tokenone", cat, 50, 100, 10L));
                rows.add(prior("tokentwo", cat, 50, 100, 10L));
            }
            when(priorRepository.findByTokens(anyCollection(), any(Pageable.class))).thenReturn(rows);

            CategorySuggestionResponse response = rank("tokenone", "tokentwo");

            assertThat(response.available()).isTrue();
            assertThat(response.suggestions())
                    .hasSize(CategorySuggestionService.MAX_SUGGESTIONS);
        }

        /**
         * Determinism to the end. A suggestion that reorders between two identical calls is worse than
         * one that is merely imprecise — an officer cannot learn to expect it, and on a multi-pod
         * deployment the same complaint would be annotated differently depending on which instance
         * answered.
         */
        @Test
        @DisplayName("is deterministic: an identical read produces an identical order")
        void orderIsDeterministic() {
            List<AssistanceCategoryPrior> rows = new ArrayList<>();
            for (long cat = 2L; cat <= 6L; cat++) {
                // Identical confidence AND identical denominators, so only the final id tiebreak can
                // settle the order.
                rows.add(prior("tokenone", cat, 50, 100, 10L));
                rows.add(prior("tokentwo", cat, 50, 100, 10L));
            }
            when(priorRepository.findByTokens(anyCollection(), any(Pageable.class))).thenReturn(rows);

            List<Long> first = rank("tokenone", "tokentwo").suggestions().stream()
                    .map(CategorySuggestionResponse.Suggestion::categoryId).toList();
            List<Long> second = rank("tokenone", "tokentwo").suggestions().stream()
                    .map(CategorySuggestionResponse.Suggestion::categoryId).toList();

            assertThat(first).isEqualTo(second).containsExactly(2L, 3L, 4L);
        }

        /**
         * The rollup is derived data and can outlive a retired category by up to one refresh interval.
         * Suggesting an id the picker does not render would produce a highlight pointing at nothing.
         */
        @Test
        @DisplayName("a category the master no longer holds is dropped, not suggested")
        void retiredCategoryIsDropped() {
            rollupHolds(
                    prior("foreclosure", MINORITY, 50, 100, MINORITY_SIZE),
                    prior("penalty", MINORITY, 50, 100, MINORITY_SIZE));
            // The master has forgotten it.
            when(categoryRepository.findAllById(any())).thenReturn(List.of());

            CategorySuggestionResponse response = rank("foreclosure", "penalty");

            assertThat(response.suggestions()).isEmpty();
        }

        @Test
        @DisplayName("carries the master's name and translation key, not an invented label")
        void carriesTheMastersLabels() {
            rollupHolds(
                    prior("foreclosure", MINORITY, 50, 100, MINORITY_SIZE),
                    prior("penalty", MINORITY, 50, 100, MINORITY_SIZE));
            when(categoryRepository.findAllById(any())).thenReturn(List.of(
                    ComplaintCategory.builder().id(MINORITY)
                            .name("Loan / Advances").labelKey("category.loan").build()));

            CategorySuggestionResponse.Suggestion suggestion =
                    suggestionFor(rank("foreclosure", "penalty"), MINORITY);

            assertThat(suggestion.name()).isEqualTo("Loan / Advances");
            assertThat(suggestion.labelKey()).isEqualTo("category.loan");
        }

        /** Confidence and lift are rounded, so a client cannot print 0.8333333333333334. */
        @Test
        @DisplayName("rounds the derived statistics to three places")
        void roundsDerivedStatistics() {
            rollupHolds(
                    prior("foreclosure", MINORITY, 5, 6, MINORITY_SIZE),
                    prior("penalty", MINORITY, 5, 6, MINORITY_SIZE));

            CategorySuggestionResponse.Suggestion suggestion =
                    suggestionFor(rank("foreclosure", "penalty"), MINORITY);

            // 5/6 = 0.8333333333333334 unrounded.
            assertThat(suggestion.confidence()).isEqualTo(0.833d);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Degradation: always a usable payload, never a 4xx and never a throw")
    class Degradation {

        /**
         * An empty token list must not reach the repository. {@code IN ()} is a syntax error on Oracle,
         * so a feature that was merely quiet in dev would FAIL in production.
         *
         * <p>MUTATION CHECK: removing the {@code tokens.isEmpty()} guard passes in any MySQL-backed test
         * and breaks the deployed engine, which is the exact class of defect this codebase has recorded.
         */
        @Test
        @DisplayName("blank text issues NO query, because IN () is a syntax error on Oracle")
        void blankTextIssuesNoQuery() {
            assertThat(service.suggest("CMP-1", "the and not for").available()).isFalse();
            assertThat(service.suggest("CMP-1", "   ").available()).isFalse();
            assertThat(service.suggest("CMP-1", (String) null).available()).isFalse();
            verifyNoInteractions(priorRepository);
        }

        /** An empty rollup — the unapplied-migration state — is the empty signal, not an error. */
        @Test
        @DisplayName("an empty rollup is the empty signal")
        void emptyRollup() {
            CategorySuggestionResponse response = service.suggest("CMP-1", "ATM withdrawal dispute");

            assertThat(response.available()).isFalse();
            assertThat(response.suggestions()).isEmpty();
            assertThat(response.complaintNumber()).isEqualTo("CMP-1");
        }

        /**
         * The read throwing — an absent table, a query timeout — must degrade to the one documented
         * success shape. The caller is a filing form that must stay usable.
         */
        @Test
        @DisplayName("a failing read degrades to the empty signal rather than throwing")
        void failingReadDegrades() {
            when(priorRepository.findByTokens(anyCollection(), any(Pageable.class)))
                    .thenThrow(new RuntimeException(
                            "Table 'cms_db.ASSISTANCE_CATEGORY_PRIOR' doesn't exist"));

            CategorySuggestionResponse response = service.suggest("CMP-1", "ATM withdrawal dispute");

            assertThat(response.available()).isFalse();
            assertThat(response.suggestions()).isEmpty();
        }

        /**
         * A row that cannot produce a share must be skipped rather than divided by. Unreachable through
         * the schema (all four columns are NOT NULL), and the alternative is a division by zero inside a
         * request path whose contract is never to fail.
         */
        @Test
        @DisplayName("a zero denominator is skipped, never divided by")
        void zeroDenominatorIsSkipped() {
            AssistanceCategoryPrior zero = AssistanceCategoryPrior.builder()
                    .token("foreclosure").categoryKey(MINORITY)
                    .occurrences(5L).tokenTotal(0L)
                    .categoryTotal(MINORITY_SIZE).labelledTotal(CORPUS)
                    .refreshedAt(LocalDateTime.now()).build();

            rollupHolds(zero, prior("penalty", MINORITY, 50, 100, MINORITY_SIZE));

            // Does not throw, and does not suggest on the strength of one usable row.
            assertThat(rank("foreclosure", "penalty").available()).isFalse();
        }

        /** The read is capped. A caller that forgot would turn index ranges into a scan. */
        @Test
        @DisplayName("passes the declared row cap to the repository")
        void passesTheRowCap() {
            rollupHolds(prior("foreclosure", MINORITY, 50, 100, MINORITY_SIZE));
            rank("foreclosure", "penalty");

            org.mockito.ArgumentCaptor<Pageable> captor =
                    org.mockito.ArgumentCaptor.forClass(Pageable.class);
            org.mockito.Mockito.verify(priorRepository)
                    .findByTokens(any(Collection.class), captor.capture());

            assertThat(captor.getValue().getPageSize())
                    .isEqualTo(AssistanceCategoryPrior.MAX_PRIOR_ROWS);
        }
    }
}
